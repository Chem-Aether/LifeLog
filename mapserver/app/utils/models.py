from pydantic import BaseModel, Field


class ValuesRequest(BaseModel):
    codes: list[str] = Field(
        default_factory=list,
        description="需要查询的代码列表；代码类型以接口用途为准",
        examples=[["CODE-1", "CODE-2"]],
    )


class IdsRequest(BaseModel):
    ids: list[str] = Field(default_factory=list, max_length=500, description="行政区节点 ID 列表，最多 500 项")


class PointsRequest(BaseModel):
    points: list[dict] = Field(
        default_factory=list,
        max_length=500,
        description="待查询坐标列表，最多 500 项；每项包含 longitude/latitude（或 lng/lat），可选 id 用于关联结果",
        examples=[[{"id": "point-1", "longitude": 118.8, "latitude": 32.0}]],
    )


class RailwayRouteRequest(BaseModel):
    trainCode: str = Field(default="", max_length=30, description="车次编号，用于辅助匹配线路，例如 G1")
    stations: list[str] = Field(
        min_length=2,
        max_length=200,
        description="按行车顺序排列的途经车站名称，至少 2 站、最多 200 站",
        examples=[["北京南", "南京南", "上海虹桥"]],
    )
