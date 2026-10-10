from fastapi import APIRouter, HTTPException, Query

from ..services.railway_network import search_stations
from ..services.railway import sample_route
from ..utils.models import RailwayRouteRequest
from ..utils.responses import payload

router = APIRouter(prefix="/api/railway", tags=["railway"])


@router.get("/stations/search", summary="搜索铁路网络车站")
def stations(keyword: str = "", limit: int = Query(20, ge=1, le=50)):
    try:
        return payload(search_stations(keyword, limit))
    except FileNotFoundError as exc:
        raise HTTPException(503, str(exc)) from exc


@router.post("/routes/sample", summary="按有序途经站采样铁路轨迹")
def route_sample(body: RailwayRouteRequest):
    try:
        return payload(sample_route(body.trainCode.strip().upper(), body.stations))
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
