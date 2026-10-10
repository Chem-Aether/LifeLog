from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware


from responses import payload, fail
from CN12306.train_no import get_train_route
from CN12306.stations import parse_station_names



app = FastAPI(
    title="Spider Service",
    version="1.0.0",
    description="12306 车站爬虫服务（无数据库，现查现返回）",
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


@app.get("/stations", tags=["station"])
def list_stations(
    train_code: str = Query(..., description="车次，如 D124"),
    date: str = Query(..., description="日期，格式 YYYYMMDD，如 20260923"),
):
    """
    查询车次经停站

    - train_code: 车次（如 D124）
    - date: 日期（如 20260923）
    """
    try:
        result = get_train_route(
            train_code=train_code.strip().upper(),
            date_yyyymmdd=date.strip(),
        )
    except Exception as e:
        return fail(f"查询失败: {e}", status_code=500)

    if not result:
        return fail(f"未找到车次 {train_code} 在 {date} 的数据", status_code=404)

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