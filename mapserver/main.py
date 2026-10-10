import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import catalog, geocoding, railway, regions, tiles
from app.settings import CORS_HEADERS, CORS_METHODS, CORS_ORIGINS, HOST, PORT
from app.utils.responses import payload


app = FastAPI(
    title="EastBill Geographic Service",
    version="1.0.0",
    description="Offline PMTiles, POI search and administrative geocoding service.",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_methods=CORS_METHODS,
    allow_headers=CORS_HEADERS,
)
app.include_router(geocoding.router)
app.include_router(regions.router)
app.include_router(catalog.router)
app.include_router(railway.router)
app.include_router(tiles.router)


@app.get("/health", tags=["system"])
def health():
    return payload({"status": "ok"})


if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT)
