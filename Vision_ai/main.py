import base64
import io
import os
import requests
import uvicorn
import datetime
from fastapi import FastAPI, UploadFile, File, Form, Response, Request
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from ultralytics import YOLO
from PIL import Image, ImageDraw, ImageFilter
from rembg import remove, new_session

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# config
FORGE_API_URL = "http://127.0.0.1:7860/sdapi/v1/img2img"
BASE_IMAGE_PATH = r"C:\Users\MrHusk\Desktop\META-PRO\VisionCam_Project\VisionCam\src\main\resources\static"
DESKTOP_PATH = r"C:\Users\MrHusk\Desktop"

print("Se încarcă YOLOv8 Medium...")
model_yolo = YOLO("yolov8m.pt")


def encode_image_to_base64(image: Image.Image) -> str:
    buffered = io.BytesIO()
    image.save(buffered, format="PNG")
    return base64.b64encode(buffered.getvalue()).decode('utf-8')


def load_product_bytes(product_image_url: str) -> bytes:
    if product_image_url.startswith("http"):
        return requests.get(product_image_url,timeout=10).content
    clean_path = product_image_url.lstrip('/')
    full_path = os.path.normpath(os.path.join(BASE_IMAGE_PATH, clean_path))
    with open(full_path, "rb") as f:
        return f.read()


def remove_background(product_bytes: bytes) -> Image.Image:
    try:
        session = new_session(providers=['CPUExecutionProvider'])
        no_bg_bytes = remove(product_bytes, session=session)
    except Exception as e:
        print(f"rembg session failed, retrying without session: {e}")
        no_bg_bytes = remove(product_bytes)
    return Image.open(io.BytesIO(no_bg_bytes)).convert("RGBA")


@app.post("/analyze")
async def analyze(file: UploadFile = File(...), known_width: int = 100):
    contents = await file.read()
    image = Image.open(io.BytesIO(contents))
    results = model_yolo(image)
    lista_obiecte = []
    mapping = {"couch": "Couch", "chair": "Chair", "bed": "Bed", "vase": "Vase","painting":"Painting"}
    for r in results:
        for box in r.boxes:
            raw_label = model_yolo.names[int(box.cls[0])]
            if raw_label in mapping:
                coords = box.xyxy[0].tolist()
                pixel_w = int(box.xywh[0][2])
                pixel_h = int(box.xywh[0][3])
                ratio_mult = (known_width * 100) // pixel_w
                real_h = (pixel_h * ratio_mult) // 100
                lista_obiecte.append({
                    "detected_item": mapping[raw_label],
                    "x": int(coords[0]), "y": int(coords[1]),
                    "w_px": pixel_w, "h_px": pixel_h,
                    "real_height_cm": int(real_h)
                })
    return {"items": lista_obiecte}


@app.post("/generate_pro")
async def generate_pro(
        room_file: UploadFile = File(...),
        product_image_url: str = Form(...),
        product_category: str = Form(...),
        x: int = Form(...), y: int = Form(...),
        w_px: int = Form(...), h_px: int = Form(...)
):
    try:
        room_bytes = await room_file.read()
        room_img = Image.open(io.BytesIO(room_bytes)).convert("RGB")
        cat_name = product_category.strip().replace(" ", "_")

        # scalare
        product_bytes = load_product_bytes(product_image_url)
        product_no_bg = remove_background(product_bytes)
        bbox = product_no_bg.getbbox()
        if bbox: product_no_bg = product_no_bg.crop(bbox)

        scale_factor = 119 if cat_name.lower() == "couch" else 104
        orig_w, orig_h = product_no_bg.size
        target_w = (w_px * scale_factor) // 100
        target_h = (orig_h * target_w) // orig_w
        product_resized = product_no_bg.resize((target_w, target_h), Image.LANCZOS)

        paste_x = x - ((target_w - w_px) // 2)
        paste_y = (y + h_px) - target_h

        composite = room_img.copy().convert("RGBA")
        composite.paste(product_resized, (paste_x, paste_y), product_resized)
        composite_rgb = composite.convert("RGB")


        # masca
        p_side, p_bottom, p_top = 30, 50, 30
        if cat_name.lower() == "couch":
            p_top = 80
            p_bottom = 30

        mask_img = Image.new("L", room_img.size, 0)
        draw = ImageDraw.Draw(mask_img)
        draw.rectangle([
            paste_x - p_side, paste_y - p_top,
            paste_x + target_w + p_side, paste_y + target_h + p_bottom
        ], fill=255)

        # Protectie
        alpha = product_resized.split()[3]
        mask_img.paste(0, (paste_x, paste_y), mask=alpha)

        mask_img = mask_img.filter(ImageFilter.GaussianBlur(radius=12))


        product_b64 = encode_image_to_base64(product_no_bg)
        composite_b64 = encode_image_to_base64(composite_rgb)
        mask_b64 = encode_image_to_base64(mask_img.convert("RGB"))

        payload = {
            "init_images": [composite_b64],
            "mask": mask_b64,
            "mask_blur": 10,
            "inpainting_fill": 1,
            "inpaint_full_res": True,
            "inpaint_full_res_padding": 32,
            "prompt": (
                f"photorealistic {product_category}, soft ambient floor shadows, "
                "clean wall background, matching room colors, 8k, highly detailed"
            ),
            "negative_prompt": (
                "pillows above, old sofa remnants, old cushions, bed, headboard, "
                "hallucinated objects, messy wall, distorted, blurry, cartoon, "
                "extra furniture, floating pieces, green fragments"
            ),
            "steps": 120,
            "cfg_scale": 7.5,
            "denoising_strength": 0.60,
            "sampler_name": "DPM++ 2M Karras",
            "alwayson_scripts": {
                "controlnet": {
                    "args": [
                        {
                            "enabled": True,
                            "module": "ip-adapter_clip_sd15",
                            "model": "ip-adapter_sd15",
                            "weight": 0.85,
                            "image": product_b64,
                            "control_mode": 2,
                            "pixel_perfect": True
                        }
                    ]
                }
            }
        }

        response = requests.post(FORGE_API_URL, json=payload, timeout=300)
        if response.status_code == 200:
            image_data = base64.b64decode(response.json()["images"][0])

            # refine
            refined_img = Image.open(io.BytesIO(image_data)).convert("RGB")
            refined_b64 = encode_image_to_base64(refined_img)

            refine_payload = {
                "init_images": [refined_b64],
                "mask": mask_b64,
                "mask_blur": 6,
                "inpainting_fill": 1,
                "inpaint_full_res": True,
                "inpaint_full_res_padding": 16,
                "prompt": (
                    f"photorealistic {product_category}, perfect fabric texture, "
                    "natural shadows on floor, seamless edges, 8k, ultra detailed"
                ),
                "negative_prompt": (
                    "old sofa remnants, green fragments, floating pieces, blurry edges, "
                    "artifacts, distorted, cartoon, duplicate furniture,blurry"
                ),
                "steps": 70,
                "cfg_scale": 6.0,
                "denoising_strength": 0.3,
                "sampler_name": "Euler a",
                "alwayson_scripts": {
                    "controlnet": {
                        "args": [
                            {
                                "enabled": True,
                                "module": "ip-adapter_clip_sd15",
                                "model": "ip-adapter_sd15",
                                "weight": 0.6,
                                "image": product_b64,
                                "control_mode": 0,
                                "pixel_perfect": True
                            }
                        ]
                    }
                }
            }

            print("DEBUG: Trimit Pass 2 Refinement...")
            refine_response = requests.post(FORGE_API_URL, json=refine_payload, timeout=300)

            if refine_response.status_code == 200:
                image_data = base64.b64decode(refine_response.json()["images"][0])
                print("DEBUG: Pass 2 OK!")
            else:
                print("DEBUG: Pass 2 fail, returnez Pass 1")

            return Response(content=image_data, media_type="image/png")

        return JSONResponse(status_code=500, content={"status": "error", "message": "Eroare Forge"})

    except Exception as e:
        print(f"EROARE: {e}")
        raise


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8001)