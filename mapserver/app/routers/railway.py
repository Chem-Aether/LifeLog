from fastapi import APIRouter, Body, HTTPException, Query

from ..services.railway_network import search_stations
from ..services.railway import sample_route
from ..utils.models import RailwayRouteRequest
from ..utils.responses import payload

router = APIRouter(prefix="/api/railway", tags=["railway"])


@router.get("/stations/search", summary="搜索铁路网络车站")
def stations(
    keyword: str = Query("", description="车站名称关键词，支持中文名称匹配"),
    limit: int = Query(20, ge=1, le=50, description="最多返回的车站数量，范围 1–50"),
):
    try:
        return payload(search_stations(keyword, limit))
    except FileNotFoundError as exc:
        raise HTTPException(503, str(exc)) from exc


@router.post("/routes/sample", summary="按有序途经站采样铁路轨迹")
def route_sample(body: RailwayRouteRequest = Body(description="提供车次编号和按行车顺序排列的车站列表")):
    try:
        return payload(sample_route(body.trainCode.strip().upper(), body.stations))
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
