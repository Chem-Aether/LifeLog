import json
import os
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = Path(os.getenv("MEDIA_CONFIG", ROOT / "config" / "application.json"))
if not CONFIG_PATH.is_absolute():
    CONFIG_PATH = ROOT / CONFIG_PATH

with CONFIG_PATH.open("r", encoding="utf-8") as stream:
    CONFIG = json.load(stream)

SERVER = CONFIG.get("server", {})
STORAGE = CONFIG.get("storage", {})
LIMITS = CONFIG.get("limits", {})
DEFAULT_CATEGORIES = {"travel": "files/travel", "diary": "files/diary", "bill": "files/bill", "shared": "files/shared"}


def _resolve_path(value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else (ROOT / path).resolve()


DATA_DIR = _resolve_path(os.getenv("MEDIA_DATA_DIR", STORAGE.get("data_dir", "data")))
CATEGORY_CONFIG = STORAGE.get("categories", DEFAULT_CATEGORIES)


def _resolve_category_path(value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else (DATA_DIR / path).resolve()


HOST = os.getenv("MEDIA_HOST", SERVER.get("host", "127.0.0.1"))
PORT = int(os.getenv("MEDIA_PORT", SERVER.get("port", 8780)))
DATABASE_PATH = DATA_DIR / STORAGE.get("database", "media.sqlite3")
CATEGORY_DIRS = {
    name: _resolve_category_path(os.getenv(f"MEDIA_STORAGE_{name.upper()}", path))
    for name, path in CATEGORY_CONFIG.items()
}
LEGACY_FILES_DIR = DATA_DIR / "files"
PUBLIC_BASE_URL = os.getenv(
    "MEDIA_PUBLIC_BASE_URL", SERVER.get("public_base_url", "http://127.0.0.1:8780")
).rstrip("/")
MAX_UPLOAD_BYTES = int(os.getenv("MEDIA_MAX_UPLOAD_BYTES", LIMITS.get("max_upload_bytes", 20 * 1024 * 1024)))
CORS_ORIGINS = [value.strip() for value in os.getenv(
    "MEDIA_CORS_ORIGINS", ",".join(SERVER.get("cors_origins", []))
).split(",") if value.strip()]
CORS_METHODS = ["GET", "POST", "DELETE", "OPTIONS"]
CORS_HEADERS = ["Content-Type", "X-Media-Key", "token"]
API_KEY = os.getenv("MEDIA_API_KEY", "")
