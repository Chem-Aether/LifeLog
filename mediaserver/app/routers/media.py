from fastapi import APIRouter, File, Form, Header, Request, UploadFile
from fastapi.responses import FileResponse, Response

from ..services.media import authorize, delete_media as remove_media
from ..services.media import get_media as read_media
from ..services.media import get_media_types as read_media_types
from ..services.media import initialize, upload_media as save_media


router = APIRouter(tags=["media"])


@router.post("/api/media", status_code=201)
async def upload_media(
    file: UploadFile = File(...),
    category: str = Form(default="shared"),
    media_type: str = Form(default="image"),
    x_media_key: str | None = Header(default=None),
):
    authorize(x_media_key)
    return await save_media(file, category, media_type)


@router.get("/api/media/types")
def get_media_types(ids: str):
    return read_media_types(ids)


@router.get("/media/{media_id}")
def get_media(media_id: str, request: Request):
    return read_media(media_id, request)


@router.delete("/api/media/{media_id}", status_code=204)
def delete_media(media_id: str, x_media_key: str | None = Header(default=None)):
    authorize(x_media_key)
    remove_media(media_id)
