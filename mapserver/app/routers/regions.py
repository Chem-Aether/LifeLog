from fastapi import APIRouter, Body, HTTPException, Path, Query

from ..utils.models import IdsRequest, PointsRequest, RegionIdsRequest
from ..utils.responses import geojson, payload
from ..services import admin_boundaries as regions


router = APIRouter(prefix="/api/regions", tags=["regions"])


def valid(longitude, latitude):
    return -180 <= longitude <= 180 and -90 <= latitude <= 90


@router.get("/reverse", summary="根据经纬度查询行政区")
def reverse(
    longitude: float | None = Query(None, description="查询点经度，范围 -180 至 180"),
    latitude: float | None = Query(None, description="查询点纬度，范围 -90 至 90"),
):
    if longitude is None or latitude is None or not valid(longitude, latitude):
        raise HTTPException(400, "经纬度参数无效")
    return payload(regions.reverse(longitude, latitude))


@router.post("/reverse/batch", summary="批量根据经纬度查询行政区")
def reverse_batch(body: PointsRequest = Body(description="提交最多 500 个待反查坐标；每项可携带 id 以便关联结果")):
    results = []
    for item in body.points:
        try:
            longitude = float(item.get("longitude", item.get("lng")))
            latitude = float(item.get("latitude", item.get("lat")))
            if not valid(longitude, latitude):
                raise ValueError
            results.append({"id": item.get("id"), **regions.reverse(longitude, latitude)})
        except (TypeError, ValueError):
            results.append({"id": item.get("id"), "error": "经纬度参数无效"})
    return payload(results)


@router.get("/search", summary="搜索行政区划信息")
def search(
    keyword: str = Query("", description="行政区名称或行政区代码关键词"),
    level: int = Query(0, ge=0, description="行政区层级；0 表示全部"),
    country_code: str | None = Query(None, description="ISO 3166-1 国家代码，如 CN"),
):
    return payload(regions.search(keyword.strip(), level, country_code) if keyword.strip() else [])


@router.post("/boundaries", summary="根据一个或多个行政区 ID 查询边界")
def boundaries(body: IdsRequest = Body(description="提交需要获取边界的行政区 ID，最多处理 500 个")):
    ids = list(dict.fromkeys(body.ids))
    features = [regions.boundary_feature(regions.boundary_row(node_id)) for node_id in ids]
    return geojson({"type": "FeatureCollection", "features": [feature for feature in features if feature]})


@router.post("/paths", summary="批量查询行政区节点及其祖先路径")
def paths(body: RegionIdsRequest = Body(description="提交足迹缓存的行政区节点 ID，批量返回对应路径；最多 500 项")):
    return payload(regions.region_records(body.ids))


@router.get("/{node_id}", summary="根据行政区 ID 查询行政区信息")
def by_id(node_id: str = Path(description="行政区节点 ID，例如 CN:2:3201")):
    record = regions.region_record(node_id)
    if not record:
        raise HTTPException(404, "行政区不存在")
    return payload(record)
