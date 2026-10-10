from pydantic import BaseModel, Field


class ValuesRequest(BaseModel):
    codes: list[str] = Field(default_factory=list)


class PointsRequest(BaseModel):
    points: list[dict] = Field(default_factory=list, max_length=500)


class RailwayRouteRequest(BaseModel):
    trainCode: str = Field(default="", max_length=30)
    stations: list[str] = Field(min_length=2, max_length=200)
