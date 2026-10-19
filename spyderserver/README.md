# 车次查询服务

`spyderserver` 是独立的 Python/FastAPI 服务，负责从 12306 在线来源查询车次经停信息及可获取的车型/担当信息。它不管理旅行记录、不连接业务数据库，也不是旅行服务的必需依赖。

## 运行

默认端口为 `8082`，入口为 `main.py`。使用项目配置的 Python 环境安装 `requirements.txt` 后运行：

```powershell
cd spyderserver
python main.py
```

默认健康检查为 `GET /health`。车站信息和车次查询接口以服务 `/docs` 及路由代码为准。

`GET /trains/G123` 通过 12306 当日车次检索及 OTN 时刻表接口获取站序、站名和到发时刻，日期由服务端按 `Asia/Shanghai` 时区确定；微信小程序车次详情接口只用于尽力补充 `train_model`、`train_owner`、`train_runner`，车型补充失败不会影响时刻表。补充字段可能为空；12306 上游未稳定提供站台信息，因此不生成或推断站台号。

`GET /trains/G123/map-line` 使用当日车次内部编号调用 12306 `getTrainMapLine`，返回已转换为 WGS-84 的 GeoJSON `LineString`。无可用地图线时返回 404；调用方可选择自己的铁路网算法作为回退。

`GET /stations/list` 只用于查询车站目录，与列车时刻表查询分开。

## 调用边界

旅行页面的在线车次信息和 mapserver 的优先列车地图线路依赖本服务。若 mapserver 无法取得列车地图线路，会独立回退到自己的铁路网络寻路；旅行记录仍可手动维护。查询依赖外部网络和上游服务可用性，不应用作旅行数据的唯一来源。
