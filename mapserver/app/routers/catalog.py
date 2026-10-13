from fastapi import APIRouter, HTTPException, Path, Query

from ..utils.database import all_rows, one
from ..utils.responses import payload
from ..services import catalog


router = APIRouter(prefix="/api", tags=["catalog"])


@router.get("/pois/search", summary="搜索兴趣点")
def pois(
    q: str | None = Query(None, description="兴趣点名称、别名或地址关键词"),
    keyword: str | None = Query(None, description="关键词备用参数；同时提供 q 时优先使用 q"),
    category: str | None = Query(None, description="按兴趣点类别筛选，例如 tourism、amenity"),
    limit: int = Query(20, ge=1, le=100, description="最多返回的兴趣点数量，范围 1–100"),
):
    return payload(catalog.search_pois((q or keyword or "").strip(), category, limit))


@router.get("/pois/nearby", summary="查询附近兴趣点")
def nearby(
    longitude: float | None = Query(None, description="中心点经度，范围 -180 至 180"),
    latitude: float | None = Query(None, description="中心点纬度，范围 -90 至 90"),
    lng: float | None = Query(None, description="longitude 的简写；未提供 longitude 时使用"),
    lat: float | None = Query(None, description="latitude 的简写；未提供 latitude 时使用"),
    radius: float = Query(5000, ge=50, le=100000, description="搜索半径，单位米，范围 50–100000"),
    category: str | None = Query(None, description="按兴趣点类别筛选"),
    limit: int = Query(20, ge=1, le=100, description="最多返回的兴趣点数量，范围 1–100"),
):
    longitude = longitude if longitude is not None else lng
    latitude = latitude if latitude is not None else lat
    if longitude is None or latitude is None or not (-180 <= longitude <= 180 and -90 <= latitude <= 90):
        raise HTTPException(400, "经纬度参数无效")
    return payload(catalog.nearby_pois(longitude, latitude, radius, category, limit))


@router.get("/pois/stats", summary="兴趣点统计信息")
def poi_stats():
    total = one("SELECT COUNT(*) AS count FROM poi")["count"]
    categories = all_rows("SELECT category, COUNT(*) AS count FROM poi GROUP BY category ORDER BY count DESC")
    metadata = {row["key"]: row["value"] for row in all_rows("SELECT key, value FROM geo_metadata WHERE key LIKE 'poi.%'")}
    return payload({"total": total, "categories": categories, "metadata": metadata})


@router.get("/pois/{identifier}", summary="根据 ID 查询兴趣点")
def poi(identifier: int = Path(description="兴趣点唯一 ID")):
    result = catalog.poi_by_id(identifier)
    if not result:
        raise HTTPException(404, "POI 不存在")
    return payload(result)
