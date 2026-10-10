import os
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = Path(os.getenv("MAP_CONFIG", ROOT / "config" / "application.json")).resolve()


def _load_config() -> dict:
    with CONFIG_PATH.open("r", encoding="utf-8") as stream:
        return json.load(stream)


def _resolve_path(value: str) -> Path:
    path = Path(value)
    return (path if path.is_absolute() else ROOT / path).resolve()


CONFIG = _load_config()
SERVER = CONFIG.get("server", {})
STORAGE = CONFIG.get("storage", {})
TILES = CONFIG.get("tiles", {})
CORS = SERVER.get("cors", {})
HOST = os.getenv("MAP_HOST", str(SERVER.get("host", "127.0.0.1")))
PORT = int(os.getenv("MAP_PORT", SERVER.get("port", 8765)))
DATA_DIR = _resolve_path(os.getenv("MAP_DATA_DIR", str(STORAGE.get("data_dir", "data"))))
POI_PATH = _resolve_path(os.getenv("MAP_POI", str(STORAGE.get("poi", "data/poi.gpkg"))))
RAILWAY_NETWORK_PATH = _resolve_path(os.getenv("MAP_RAILWAY_NETWORK", str(STORAGE.get("railway_network", "data/china_railway_network.gpkg"))))
ADMIN_BOUNDARIES_PATH = _resolve_path(os.getenv("MAP_ADMIN_BOUNDARIES", str(STORAGE.get("admin_boundaries", "data/china_admin_boundaries.gpkg"))))
AIRPORTS_PATH = _resolve_path(os.getenv("MAP_AIRPORTS", str(STORAGE.get("airports", "data/airports.gpkg"))))
TILE_CACHE_SIZE = int(os.getenv("MAP_TILE_CACHE_SIZE", TILES.get("cache_size", 512)))
TILE_MIN_ZOOM = int(TILES.get("min_zoom", 0))
TILE_MAX_ZOOM = int(TILES.get("max_zoom", 18))
CORS_ORIGINS = CORS.get("origins", ["*"])
CORS_METHODS = CORS.get("methods", ["GET", "POST", "HEAD", "OPTIONS"])
CORS_HEADERS = CORS.get("headers", ["*"])


def load_tile_sources() -> list[dict]:
    return _load_config().get("tiles", {}).get("sources", [])
