import json
import os
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
config_path = Path(os.getenv("MEDIA_CONFIG", ROOT / "config" / "application.json"))
if not config_path.is_absolute():
    config_path = ROOT / config_path
CONFIG = json.loads(config_path.read_text(encoding="utf-8"))
server = CONFIG.get("server", {})
storage = CONFIG.get("storage", {})
limits = CONFIG.get("limits", {})
DEFAULT_CATEGORIES = {"travel": "data/files/travel", "diary": "data/files/diary", "bill": "data/files/bill", "shared": "data/files/shared"}


def resolve_path(value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else (ROOT / path).resolve()


data_dir = resolve_path(os.getenv("MEDIA_DATA_DIR", storage.get("data_dir", "data")))
category_config = storage.get("categories", DEFAULT_CATEGORIES)


def resolve_category_path(value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else (data_dir / path).resolve()


class Settings:
    host = os.getenv("MEDIA_HOST", server.get("host", "127.0.0.1"))
    port = int(os.getenv("MEDIA_PORT", server.get("port", 8780)))
    data_dir = data_dir
    database = data_dir / storage.get("database", "media.sqlite3")
    category_dirs = {
        name: resolve_category_path(os.getenv(f"MEDIA_STORAGE_{name.upper()}", path))
        for name, path in category_config.items()
    }
    legacy_files_dir = data_dir / "files"
    public_base_url = os.getenv(
        "MEDIA_PUBLIC_BASE_URL", server.get("public_base_url", "http://127.0.0.1:8780")
    ).rstrip("/")
    max_upload_bytes = int(os.getenv("MEDIA_MAX_UPLOAD_BYTES", limits.get("max_upload_bytes", 20 * 1024 * 1024)))
    cors_origins = [value.strip() for value in os.getenv(
        "MEDIA_CORS_ORIGINS", ",".join(server.get("cors_origins", []))
    ).split(",") if value.strip()]
    api_key = os.getenv("MEDIA_API_KEY", "")


settings = Settings()
