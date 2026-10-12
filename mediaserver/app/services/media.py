import sqlite3
import shutil
import tempfile
import uuid
import warnings
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator

from fastapi import APIRouter, File, Form, Header, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse, Response
from PIL import Image, UnidentifiedImageError

from ..settings import (
    API_KEY,
    CATEGORY_DIRS,
    DATABASE_PATH,
    LEGACY_FILES_DIR,
    MAX_UPLOAD_BYTES,
    PUBLIC_BASE_URL,
)


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
VIDEO_FORMATS = {
    ".mp4": "video/mp4",
    ".m4v": "video/mp4",
    ".mov": "video/quicktime",
    ".webm": "video/webm",
    ".3gp": "video/3gpp",
    ".avi": "video/x-msvideo",
}


@contextmanager
def connect() -> Iterator[sqlite3.Connection]:
    connection = sqlite3.connect(DATABASE_PATH)
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
    for directory in CATEGORY_DIRS.values():
        directory.mkdir(parents=True, exist_ok=True)
    DATABASE_PATH.parent.mkdir(parents=True, exist_ok=True)
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
                old_path = LEGACY_FILES_DIR / stored_name
                new_path = CATEGORY_DIRS["shared"] / relative_path
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
    root = CATEGORY_DIRS.get(category)
    if root is None:
        raise HTTPException(status_code=404, detail="Media not found")
    path = (root / relative_path).resolve()
    if not path.is_relative_to(root.resolve()):
        raise HTTPException(status_code=404, detail="Media not found")
    return path


def authorize(api_key: str | None) -> None:
    if API_KEY and api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid media API key")


async def upload_media(
    file: UploadFile = File(...),
    category: str = Form(default="shared"),
    media_type: str = Form(default="image"),
    x_media_key: str | None = Header(default=None),
):
    authorize(x_media_key)
    if category not in CATEGORY_DIRS:
        raise HTTPException(status_code=400, detail="Unsupported media category")
    if media_type not in {"image", "video"}:
        raise HTTPException(status_code=400, detail="Unsupported media type")
    category_dir = CATEGORY_DIRS[category]
    temporary_path: Path | None = None
    stored_path: Path | None = None
    try:
        max_size = min(MAX_UPLOAD_BYTES, 20 * 1024 * 1024) if media_type == "image" else 1024 * 1024 * 1024
        with tempfile.NamedTemporaryFile(dir=category_dir, prefix="upload-", delete=False) as temp:
            temporary_path = Path(temp.name)
            size = 0
            while chunk := await file.read(1024 * 1024):
                size += len(chunk)
                if size > max_size:
                    raise HTTPException(status_code=413, detail="Media exceeds upload size limit")
                temp.write(chunk)

        if media_type == "image":
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
            content_type, extension = FORMATS[image_format]
        else:
            extension = Path(file.filename or "").suffix.lower()
            content_type = VIDEO_FORMATS.get(extension)
            if content_type is None:
                raise HTTPException(status_code=415, detail="Unsupported video format")
            with temporary_path.open("rb") as video:
                if video.read(12)[4:8] != b"ftyp" and extension in {".mp4", ".m4v", ".mov"}:
                    raise HTTPException(status_code=415, detail="Invalid MP4/MOV video data")

        media_id = uuid.uuid4().hex
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
            "url": f"{PUBLIC_BASE_URL}/media/{media_id}",
            "category": category,
            "content_type": content_type,
            "media_type": "video" if content_type.startswith("video/") else "image",
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


def get_media_types(ids: str):
    media_ids = list(dict.fromkeys(value for value in ids.split(",") if value))
    if len(media_ids) > 200 or any(len(value) != 32 for value in media_ids):
        raise HTTPException(status_code=400, detail="Invalid media IDs")
    if not media_ids:
        return {}
    placeholders = ",".join("?" for _ in media_ids)
    with connect() as connection:
        rows = connection.execute(
            f"SELECT media_id,content_type FROM media WHERE media_id IN ({placeholders})", media_ids
        ).fetchall()
    return {row["media_id"]: row["content_type"] for row in rows}


def get_media(media_id: str, request: Request):
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
    headers = {"Cache-Control": "public, max-age=31536000, immutable", "X-Content-Type-Options": "nosniff"}
    if row["content_type"].startswith("video/"):
        start = 0
        range_header = request.headers.get("range")
        if range_header:
            try:
                unit, value = range_header.split("=", 1)
                first, _, last = value.partition("-")
                if unit != "bytes" or not first:
                    raise ValueError
                start = int(first)
                end = min(int(last), path.stat().st_size - 1) if last else path.stat().st_size - 1
                if start > end or start >= path.stat().st_size:
                    raise ValueError
            except ValueError:
                return Response(status_code=416, headers={"Content-Range": f"bytes */{path.stat().st_size}"})
            length = end - start + 1
            headers.update({"Content-Range": f"bytes {start}-{end}/{path.stat().st_size}", "Accept-Ranges": "bytes", "Content-Length": str(length)})
            with path.open("rb") as video:
                video.seek(start)
                return Response(video.read(length), status_code=206, media_type=row["content_type"], headers=headers)
        headers["Accept-Ranges"] = "bytes"
    return FileResponse(path, media_type=row["content_type"], headers=headers)


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
