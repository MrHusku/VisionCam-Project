# VisionCam — AI Furniture Visualization System

A local full-stack application that uses computer vision and AI image generation to detect furniture in a room photo and replace it with products from a catalog — giving users a realistic preview of how new furniture would look in their space.

---

## How It Works

1. User uploads a photo of their room
2. The system detects existing furniture using **YOLOv8**
3. The detected furniture is measured and matched against a product catalog
4. Using **Stable Diffusion inpainting** + **ControlNet IP-Adapter**, the old furniture is replaced with the selected product
5. The result is a photorealistic image of the room with the new furniture

---

## Architecture

```
Frontend (Spring MVC)
        │
        ▼
Java Backend (Spring Boot)      ←──  MySQL Database
        │                              (users, furniture, products)
        ▼
Python AI Server (FastAPI)
        │
        ├── YOLOv8 (object detection)
        ├── rembg (background removal)
        └── Stable Diffusion Forge (inpainting)
```

---

## Features

### Single Product Replacement (`/generate_pro`)
- Upload a room image and select a product from the catalog
- The AI detects the furniture position, removes the background from the product image, and inpaints it into the room
- Two-pass refinement for higher quality results (Pass 1: placement, Pass 2: texture & edges)

### Multiple Replacement (`generareMultipla`)
- Analyzes the entire room in one pass
- Detects all supported furniture items simultaneously
- Replaces each item sequentially using the first available product in that category
- Each generation step uses the output of the previous one, building up the final result progressively

### Furniture Analysis & Recommendations
- Upload a photo with a known reference width
- System detects the furniture, estimates real-world dimensions
- Returns similar products from the catalog based on category and size

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot, Spring Security |
| AI Server | Python, FastAPI, Uvicorn |
| Object Detection | YOLOv8 Medium (`yolov8m.pt`) |
| Image Generation | Stable Diffusion (via Automatic1111 Forge API) |
| Style Transfer | ControlNet IP-Adapter (`ip-adapter_sd15`) |
| Background Removal | rembg |
| Database | MySQL (via Spring Data JPA) |
| HTTP Client | Spring RestTemplate |

---

## Supported Furniture Categories

- Couch / Sofa
- Chair
- Bed
- Vase
- Painting

> Note: YOLOv8 may label the same item as either "couch" or "sofa" depending on angle and confidence. The system normalizes both to "Couch" automatically.

---

## Local Setup

### Requirements

- Java 17+
- Python 3.10+
- [Automatic1111 Stable Diffusion WebUI Forge](https://github.com/lllyasviel/stable-diffusion-webui-forge)
- ControlNet extension with `ip-adapter_sd15` model
- MySQL

### Python Server

```bash
pip install fastapi uvicorn ultralytics pillow rembg requests
python main.py
```

Runs on `http://127.0.0.1:8001`

### Stable Diffusion Forge

Start with API enabled:
```bash
./webui.bat --api
```

Runs on `http://127.0.0.1:7860`

### Java Backend

Configure `application.properties` with your MySQL credentials, then:

```bash
./mvnw spring-boot:run
```

---

## API Endpoints (Python Server)

### `POST /analyze`
Detects furniture in an image and returns position + estimated dimensions.

| Parameter | Type | Description |
|-----------|------|-------------|
| `file` | multipart | Room image |
| `known_width` | int | Real-world width reference in cm |

**Response:**
```json
{
  "items": [
    {
      "detected_item": "Couch",
      "x": 120, "y": 340,
      "w_px": 450, "h_px": 210,
      "real_height_cm": 85
    }
  ]
}
```

### `POST /generate_pro`
Generates an inpainted image with a product placed in the room.

| Parameter | Type | Description |
|-----------|------|-------------|
| `room_file` | multipart | Room image |
| `product_image_url` | string | URL or local path to product image |
| `product_category` | string | Category name (e.g. "Couch") |
| `x`, `y` | int | Top-left position of the furniture |
| `w_px`, `h_px` | int | Bounding box size in pixels |

**Response:** PNG image (binary)

---

## Project Structure

```
├── Java Backend
│   ├── controller/       # REST controllers
│   ├── service/
│   │   └── FurnitureService.java   # Core business logic
│   ├── model/            # Furniture, Product, User, AiResponse
│   ├── repository/       # JPA repositories
│   ├── exceptions/       # Custom exceptions
│   └── security/         # Spring Security config
│
└── Python AI Server
    └── main.py           # FastAPI server (YOLO + rembg + Forge)
```

---

## Notes

- This project is designed to run **fully locally** — no external cloud services required
- The Python server and Forge must both be running before starting the Java backend
- Debug images (`BRUT_`, `MASCA_`, `POZA_FINALA`) are saved to Desktop during generation and can be deleted after review
