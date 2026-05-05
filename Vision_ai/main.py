import base64
import io
import os
import requests
import uvicorn
from fastapi import FastAPI, UploadFile, File, Form, Response, Request
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from ultralytics import YOLO
from PIL import Image, ImageDraw
from rembg import remove, new_session

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

FORGE_API_URL = "http://127.0.0.1:7860/sdapi/v1/img2img"
BASE_IMAGE_PATH = r"C:\Users\MrHusk\Desktop\META-PRO\VisionCam_Project\VisionCam\src\main\resources\static"

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    error_str = str(exc)
    print(f"DEBUG EROARE: {error_str}")
    if "out of memory" in error_str.lower():
        return JSONResponse(
            status_code=500,
            content={"status": "error", "message": "Memorie video insuficienta. Incearca o poza mai mica."}
        )
    return JSONResponse(
        status_code=500,
        content={"status": "error", "message": f"Eroare interna: {error_str}"}
    )

print("Se incarca YOLO...")
model_yolo = YOLO("yolov8n.pt")
print("API-ul Dispecer este gata! Astept conexiunea cu Forge...")

def encode_image_to_base64(image):
    buffered = io.BytesIO()
    image.save(buffered, format="PNG")
    return base64.b64encode(buffered.getvalue()).decode('utf-8')

def load_product_bytes(product_image_url: str) -> bytes:
    if product_image_url.startswith("http"):
        response_img = requests.get(product_image_url)
        return response_img.content
    elif os.path.isabs(product_image_url):
        full_path = os.path.normpath(product_image_url)
    else:
        clean_relative_path = product_image_url.lstrip('/')
        full_path = os.path.normpath(os.path.join(BASE_IMAGE_PATH, clean_relative_path))

    print(f"DEBUG: Deschid fisierul: {full_path}")
    if not os.path.exists(full_path):
        raise Exception(f"Fisierul nu exista: {full_path}")
    with open(full_path, "rb") as f:
        return f.read()

def remove_background(product_bytes: bytes) -> Image.Image:
    try:
        cpu_session = new_session(providers=['CPUExecutionProvider'])
        no_bg_bytes = remove(product_bytes, session=cpu_session)
        print("DEBUG: rembg cu sesiune OK")
    except Exception as e:
        print(f"DEBUG: rembg sesiune fail: {e}")
        no_bg_bytes = remove(product_bytes)
        print("DEBUG: rembg fallback OK")
    return Image.open(io.BytesIO(no_bg_bytes)).convert("RGBA")

@app.post("/analyze")
async def analyze(file: UploadFile = File(...), known_width: int = 100):
    contents = await file.read()
    image = Image.open(io.BytesIO(contents))
    results = model_yolo(image)

    for r in results:
        for box in r.boxes:
            label = model_yolo.names[int(box.cls[0])]
            if label in ["sofa", "couch", "bed"]:
                coords = box.xyxy[0].tolist()
                xmin, ymin, xmax, ymax = coords
                pixel_w = int(box.xywh[0][2])
                pixel_h = int(box.xywh[0][3])

                ratio_mult = (known_width * 100) // pixel_w
                real_h = (pixel_h * ratio_mult) // 100

                return {
                    "detected_item": label.capitalize(),
                    "x": int(xmin), "y": int(ymin),
                    "w_px": pixel_w, "h_px": pixel_h,
                    "real_height_cm": real_h
                }
    return {"detected_item": "Unknown"}

@app.post("/generate_pro")
async def generate_pro(
        room_file: UploadFile = File(...),
        product_image_url: str = Form(...),
        x: int = Form(...), y: int = Form(...),
        w_px: int = Form(...), h_px: int = Form(...)
):
    try:
        room_bytes = await room_file.read()
        room_img = Image.open(io.BytesIO(room_bytes)).convert("RGB")
        print(f"DEBUG: room image OK - dimensiune {room_img.size}")

        product_bytes = load_product_bytes(product_image_url)
        print(f"DEBUG: product_bytes lungime={len(product_bytes)}")

        product_no_bg = remove_background(product_bytes)
        print(f"DEBUG: product fara fundal OK - dimensiune {product_no_bg.size}")

        product_resized = product_no_bg.resize((w_px, h_px), Image.LANCZOS)
        print(f"DEBUG: product redimensionat la {w_px}x{h_px}")

        composite = room_img.copy().convert("RGBA")
        paste_x = max(0, min(x, room_img.width - w_px))
        paste_y = max(0, min(y, room_img.height - h_px))
        composite.paste(product_resized, (paste_x, paste_y), product_resized)
        composite_rgb = composite.convert("RGB")
        composite_b64 = encode_image_to_base64(composite_rgb)
        print(f"DEBUG: composite creat OK - plasat la ({paste_x}, {paste_y})")

        padding = 35 # extindem dimensiunile unde AI modifica
        mask_img = Image.new("RGB", room_img.size, (0, 0, 0))
        draw = ImageDraw.Draw(mask_img)
        draw.rectangle([
            max(0, paste_x - padding),
            max(0, paste_y - padding),
            min(room_img.width, paste_x + w_px + padding),
            min(room_img.height, paste_y + h_px + padding)
        ], fill=(255, 255, 255))
        mask_b64 = encode_image_to_base64(mask_img)
        print("DEBUG: mask creat OK")
        composite_rgb.save("C:/Users/MrHusk/Desktop/debug_composite.png")
        mask_img.save("C:/Users/MrHusk/Desktop/debug_mask.png")
        print("DEBUG: composite si mask salvate!")
        # 6. Trimite la Forge doar pentru blending realist
        payload = {
            "init_images": [composite_b64],
            "mask": mask_b64,
            "inpainting_fill": 1,
            "inpaint_full_res": True,
            "inpaint_full_res_padding": 20,

            "steps": 30, # nr de pasi de procesare
            "cfg_scale": 7, #cat de mult respecta promptu
            "denoising_strength": 0.45,  # 0 nu schimba nimic 1 ignora complet poza noua
            "mask_blur": 6,  # ← blur mic la margini
            "only_masked_padding_pixels": 20,  # ← Forge procesează DOAR zona mascată
            "prompt": "a sofa in a living room, photorealistic, natural lighting, natural shadows, seamless integration",
            "negative_prompt": "ugly, blurry, distorted, duplicate, extra furniture, floating",
        }

        print(f"DEBUG: Trimit request la Forge: {FORGE_API_URL}")
        response = requests.post(FORGE_API_URL, json=payload, timeout=300)
        print(f"DEBUG: Forge status={response.status_code}")

        if response.status_code == 200:
            result_b64 = response.json()["images"][0]
            image_data = base64.b64decode(result_b64)
            print("DEBUG: Imagine generata cu succes!")
            return Response(content=image_data, media_type="image/png")
        else:
            print(f"DEBUG: Forge error: {response.text[:500]}")
            return JSONResponse(
                status_code=500,
                content={"status": "error", "message": "Eroare Forge: " + response.text[:200]}
            )

    except Exception as e:
        import traceback
        print(f"DEBUG EXCEPTIE:\n{traceback.format_exc()}")
        raise

if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8001)