from fastapi import APIRouter, HTTPException, Request, Response

from ..settings import DATA_DIR, TILE_CACHE_SIZE, TILE_MAX_ZOOM, TILE_MIN_ZOOM, load_tile_sources
from ..tiles.registry import TileSourceRegistry
from ..tiles.service import TileService
from ..utils.responses import payload


router = APIRouter(prefix="/api", tags=["tiles"])
registry = TileSourceRegistry(load_tile_sources, DATA_DIR)
service = TileService(registry, TILE_CACHE_SIZE)


@router.get("/tiles/tilejson.json", summary="获取 TileJSON 配置")
def tilejson(request: Request):
    base = str(request.base_url).rstrip("/")
    return {
        "tilejson": "3.0.0",
        "name": "EastBill unified basemap",
        "scheme": "xyz",
        "tiles": [f"{base}/api/tiles/{{z}}/{{x}}/{{y}}.mvt"],
        "minzoom": TILE_MIN_ZOOM,
        "maxzoom": TILE_MAX_ZOOM,
        "bounds": [-180, -85.051129, 180, 85.051129],
        "attribution": "© OpenStreetMap contributors",
    }


@router.get("/tiles/{z}/{x}/{y}.mvt", summary="获取矢量瓦片")
def vector_tile(z: int, x: int, y: int):
    if z < 0 or z > 22 or x < 0 or y < 0 or x >= (1 << z) or y >= (1 << z):
        raise HTTPException(400, "Invalid tile coordinate")
    result = service.get(z, x, y)
    if result is None:
        return Response(status_code=204)
    content, source_id, overzoomed = result
    return Response(content, media_type="application/vnd.mapbox-vector-tile", headers={
        "Content-Encoding": "gzip",
        "Cache-Control": "public, max-age=86400",
        "X-Map-Source": source_id,
        "X-Map-Overzoom": str(overzoomed).lower(),
    })


@router.get("/maps/catalog", summary="获取地图源目录")
def map_catalog():
    return payload(registry.catalog())


@router.get("/maps/status", summary="获取地图源状态")
def map_status():
    return payload({"sources": registry.catalog(), "cache": service.get.cache_info()._asdict()})


@router.post("/admin/maps/reload", summary="重新加载地图源配置")
def reload_maps():
    service.reload()
    return payload({"sources": registry.catalog()})
