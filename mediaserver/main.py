from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import media
from app.services.media import initialize
from app.settings import CORS_HEADERS, CORS_METHODS, CORS_ORIGINS, HOST, PORT


@asynccontextmanager
async def lifespan(_app: FastAPI):
    initialize()
    yield


app = FastAPI(title="EastBill Media Service", version="1.0.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_methods=CORS_METHODS,
    allow_headers=CORS_HEADERS,
)
app.include_router(media.router)


@app.get("/health", tags=["system"])
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT)
