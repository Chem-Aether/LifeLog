# 车次查询服务

`spyderserver` 是独立的 Python/FastAPI 服务，负责从在线来源查询 12306 车次及经停信息。它不管理旅行记录、不连接业务数据库，也不是旅行服务的必需依赖。

## 运行

默认端口为 `8082`，入口为 `main.py`。使用项目配置的 Python 环境安装 `requirements.txt` 后运行：

```powershell
cd spyderserver
python main.py
```

默认健康检查为 `GET /health`。车站信息和车次查询接口以服务 `/docs` 及路由代码为准。

## 调用边界

仅在使用旅行页面的在线自动查车次功能时需要启动本服务。手工编辑列车、通过 mapserver 查询已有空间数据或按途经站筛选，都不要求它运行。查询依赖外部网络和上游服务可用性，不应用作旅行数据的唯一来源。
