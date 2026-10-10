from fastapi.responses import JSONResponse


def payload(data=None, message="操作成功"):
    return {"msg": message, "data": data}


def geojson(data, status_code=200):
    return JSONResponse(data, status_code=status_code, media_type="application/geo+json")
