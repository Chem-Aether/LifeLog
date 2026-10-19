from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware


from responses import payload, fail
from CN12306.train_no import get_train_map_line, get_train_route
from CN12306.stations import parse_station_names



app = FastAPI(
    title="Spider Service",
    version="1.0.0",
    description="对外爬虫接口服务",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", tags=["system"])
def health():
    return payload({"status": "ok"})


@app.get("/trains/{train_code}", tags=["train"])
def get_train(
    train_code: str,
):
    """
    查询列车时刻表及列车详情。

    - train_code: 车次（如 D124）
    - 查询日期由服务端按 Asia/Shanghai 时区自动取当天
    """
    try:
        result = get_train_route(train_code=train_code.strip().upper())
    except Exception as e:
        return fail(f"查询失败: {e}", status_code=500)

    if not result:
        return fail(f"未找到车次 {train_code} 当日的数据", status_code=404)

    return payload(result)


@app.get("/trains/{train_code}/map-line", tags=["train"])
def get_train_map(
    train_code: str,
):
    """查询 12306 提供的列车地图线路几何。"""
    try:
        result = get_train_map_line(train_code.strip().upper())
    except Exception as e:
        return fail(f"查询列车地图线路失败: {e}", status_code=502)

    if not result:
        return fail(f"未找到车次 {train_code} 的地图线路", status_code=404)
    return payload(result)


@app.get("/stations/list", tags=["station"])
def list_all_stations():
    """
    查询所有车站列表
    """
    try:
        stations = parse_station_names()
    except Exception as e:
        return fail(f"查询失败: {e}", status_code=500)

    if not stations:
        return fail("未找到车站数据", status_code=404)

    return payload(stations)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8082, reload=True)
