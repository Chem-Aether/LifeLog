from fastapi import APIRouter, Body, Query

from ..services import airports as airport_service
from ..utils.models import ValuesRequest
from ..utils.responses import payload


router = APIRouter(prefix="/api", tags=["airports"])


@router.get("/airports/search", summary="搜索机场")
def airports(
    keyword: str = Query("", description="机场名称、城市名或 ICAO/IATA 代码关键词"),
    limit: int = Query(20, ge=1, le=50, description="最多返回的机场数量，范围 1–50"),
):
    return payload(airport_service.search(keyword.strip(), limit))


@router.post("/airports/by-codes", summary="根据 ICAO/IATA 代码查询机场")
def airports_by_codes(body: ValuesRequest = Body(description="填写需要查询的 ICAO 或 IATA 机场代码列表")):
    return payload(airport_service.by_codes(body.codes))
