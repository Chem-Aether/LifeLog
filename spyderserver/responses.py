from fastapi.responses import JSONResponse


def payload(data=None, message="操作成功"):
    return {"msg": message, "data": data}



def fail(message="操作失败", data=None, status_code=400):
    return JSONResponse(
        status_code=status_code,
        content=payload(data, message),
    )