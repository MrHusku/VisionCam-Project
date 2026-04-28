from fastapi import FastAPI, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/")
def home():
    return {"status": "VisionAI is ONLINE", "message": "Ready for furniture detection"}

@app.post("/analyze")
async def analyze(file: UploadFile = File(...)):
    return {
        "detected_item": "Canapea Test",
        "width": 200,
        "height": 90,
        "confidence": 0.99
    }

if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8000)