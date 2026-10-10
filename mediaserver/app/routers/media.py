import sqlite3
import shutil
import tempfile
import uuid
import warnings
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator

from fastapi import APIRouter, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse
from PIL import Image, UnidentifiedImageError

from ..settings import settings


router = APIRouter(tags=["media"])
Image.MAX_IMAGE_PIXELS = 40_000_000
warnings.simplefilter("error", Image.DecompressionBombWarning)
FORMATS = {
    "JPEG": ("image/jpeg", ".jpg"),
    "MPO": ("image/jpeg", ".jpg"),
    "PNG": ("image/png", ".png"),
    "WEBP": ("image/webp", ".webp"),
    "GIF": ("image/gif", ".gif"),
}


@contextmanager
def connect() -> Iterator[sqlite3.Connection]:
    connection = sqlite3.connect(settings.database)
    connection.row_factory = sqlite3.Row
    try:
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def initialize() -> None:
    for directory in settings.category_dirs.values():
        directory.mkdir(parents=True, exist_ok=True)
    settings.database.parent.mkdir(parents=True, exist_ok=True)
    with connect() as connection:
        connection.execute("""
            CREATE TABLE IF NOT EXISTS media (
                media_id TEXT PRIMARY KEY,
                category TEXT NOT NULL DEFAULT 'shared',
                relative_path TEXT NOT NULL,
                original_name TEXT NOT NULL,
                content_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                uploaded_at TEXT NOT NULL
            )
        """)
        columns = {row["name"] for row in connection.execute("PRAGMA table_info(media)")}
        if "stored_name" in columns:
            if "category" not in columns:
                connection.execute("ALTER TABLE media ADD COLUMN category TEXT NOT NULL DEFAULT 'shared'")
            if "relative_path" not in columns:
                connection.execute("ALTER TABLE media ADD COLUMN relative_path TEXT")
            rows = connection.execute(
                "SELECT media_id,stored_name,uploaded_at,relative_path FROM media"
            ).fetchall()
            for row in rows:
                if row["relative_path"]:
                    continue
                try:
                    uploaded = datetime.fromisoformat(row["uploaded_at"].replace("Z", "+00:00"))
                    month_path = uploaded.strftime("%Y/%m")
                except (TypeError, ValueError):
                    month_path = "legacy"
                stored_name = Path(row["stored_name"]).name
                relative_path = Path(month_path, stored_name).as_posix()
                old_path = settings.legacy_files_dir / stored_name
                new_path = settings.category_dirs["shared"] / relative_path
                new_path.parent.mkdir(parents=True, exist_ok=True)
                if old_path.is_file() and not new_path.exists():
                    shutil.move(str(old_path), str(new_path))
                connection.execute(
                    "UPDATE media SET category='shared',relative_path=? WHERE media_id=?",
                    (relative_path, row["media_id"]),
                )
            connection.execute("""
                CREATE TABLE media_new (
                    media_id TEXT PRIMARY KEY,
                    category TEXT NOT NULL,
                    relative_path TEXT NOT NULL,
                    original_name TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    uploaded_at TEXT NOT NULL
                )
            """)
            connection.execute("""
                INSERT INTO media_new
                SELECT media_id,category,relative_path,original_name,content_type,size_bytes,uploaded_at FROM media
            """)
            connection.execute("DROP TABLE media")
            connection.execute("ALTER TABLE media_new RENAME TO media")


def media_path(category: str, relative_path: str) -> Path:
    root = settings.category_dirs.get(category)
    if root is None:
        raise HTTPException(status_code=404, detail="Media not found")
    path = (root / relative_path).resolve()
    if not path.is_relative_to(root.resolve()):
        raise HTTPException(status_code=404, detail="Media not found")
    return path


def authorize(api_key: str | None) -> None:
    if settings.api_key and api_key != settings.api_key:
        raise HTTPException(status_code=401, detail="Invalid media API key")


@router.post("/api/media", status_code=201)
async def upload_media(
    file: UploadFile = File(...),
    category: str = Form(default="shared"),
    x_media_key: str | None = Header(default=None),
):
    authorize(x_media_key)
    if category not in settings.category_dirs:
        raise HTTPException(status_code=400, detail="Unsupported media category")
    category_dir = settings.category_dirs[category]
    temporary_path: Path | None = None
    stored_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(dir=category_dir, prefix="upload-", delete=False) as temp:
            temporary_path = Path(temp.name)
            size = 0
            while chunk := await file.read(1024 * 1024):
                size += len(chunk)
                if size > settings.max_upload_bytes:
                    raise HTTPException(status_code=413, detail="Image exceeds upload size limit")
                temp.write(chunk)

        try:
            with Image.open(temporary_path) as image:
                image_format = image.format
                if image_format not in FORMATS:
                    raise HTTPException(status_code=415, detail="Only JPEG, PNG, WebP, or GIF images are accepted")
                image.verify()
        except HTTPException:
            raise
        except (UnidentifiedImageError, OSError, Image.DecompressionBombError, Image.DecompressionBombWarning) as error:
            raise HTTPException(status_code=415, detail="Image data is invalid or corrupted") from error

        media_id = uuid.uuid4().hex
        content_type, extension = FORMATS[image_format]
        stored_name = f"{media_id}{extension}"
        month_path = datetime.now(timezone.utc).strftime("%Y/%m")
        relative_path = Path(month_path, stored_name).as_posix()
        stored_path = category_dir / relative_path
        stored_path.parent.mkdir(parents=True, exist_ok=True)
        temporary_path.replace(stored_path)
        with connect() as connection:
            connection.execute(
                "INSERT INTO media (media_id,category,relative_path,original_name,content_type,size_bytes,uploaded_at) VALUES (?,?,?,?,?,?,?)",
                (media_id, category, relative_path, Path(file.filename or "image").name, content_type, size,
                 datetime.now(timezone.utc).isoformat()),
            )
        return {
            "media_id": media_id,
            "url": f"{settings.public_base_url}/media/{media_id}",
            "category": category,
            "content_type": content_type,
            "size_bytes": size,
        }
    except HTTPException:
        if stored_path:
            stored_path.unlink(missing_ok=True)
        raise
    except sqlite3.Error as error:
        if stored_path:
            stored_path.unlink(missing_ok=True)
        raise HTTPException(status_code=500, detail="Could not save media metadata") from error
    finally:
        if temporary_path:
            temporary_path.unlink(missing_ok=True)
        await file.close()


@router.get("/media/{media_id}")
def get_media(media_id: str):
    if len(media_id) != 32 or any(char not in "0123456789abcdef" for char in media_id):
        raise HTTPException(status_code=404, detail="Media not found")
    with connect() as connection:
        row = connection.execute(
            "SELECT category,relative_path,content_type FROM media WHERE media_id = ?", (media_id,)
        ).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="Media not found")
    path = media_path(row["category"], row["relative_path"])
    if not path.is_file():
        raise HTTPException(status_code=404, detail="Media file not found")
    return FileResponse(
        path,
        media_type=row["content_type"],
        headers={"Cache-Control": "public, max-age=31536000, immutable", "X-Content-Type-Options": "nosniff"},
    )


@router.delete("/api/media/{media_id}", status_code=204)
def delete_media(media_id: str, x_media_key: str | None = Header(default=None)):
    authorize(x_media_key)
    if len(media_id) != 32 or any(char not in "0123456789abcdef" for char in media_id):
        raise HTTPException(status_code=404, detail="Media not found")
    with connect() as connection:
        row = connection.execute("SELECT category,relative_path FROM media WHERE media_id = ?", (media_id,)).fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="Media not found")
        connection.execute("DELETE FROM media WHERE media_id = ?", (media_id,))
    media_path(row["category"], row["relative_path"]).unlink(missing_ok=True)
