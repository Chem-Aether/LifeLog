from fastapi import APIRouter, HTTPException, Query

from ..utils.models import ValuesRequest
from ..utils.responses import geojson, payload
from ..services import admin_boundaries as regions


router = APIRouter(prefix="/api/regions", tags=["regions"])


@router.get("/search", summary="搜索行政区划信息")
def search(keyword: str = "", level: int = Query(0, ge=0, le=3)):
    return payload(regions.search(keyword.strip(), level) if keyword.strip() else [])


@router.post("/boundaries/by-codes", summary="根据行政区划代码查询行政区边界(可批量)")
def boundaries(body: ValuesRequest):
    codes = list(dict.fromkeys(body.codes))[:500]
    features = [regions.boundary_feature(regions.boundary_row(code)) for code in codes]
    return geojson({"type": "FeatureCollection", "features": [feature for feature in features if feature]})


@router.get("/{code}/boundary", summary="根据行政区划代码查询行政区边界")
def boundary(code: str):
    feature = regions.boundary_feature(regions.boundary_row(code))
    if not feature:
        raise HTTPException(404, "行政区边界不存在")
    return geojson(feature)


@router.get("/{code}", summary="根据行政区划代码查询行政区信息")
def by_code(code: str):
    return payload(regions.region_record(code))
