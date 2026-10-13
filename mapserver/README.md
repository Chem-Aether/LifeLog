# 地图服务

地图服务是独立的 Python/FastAPI 空间数据服务。它通过 HTTP 向前端和旅行服务提供地图瓦片、地理检索及空间数据，不拥有账单、旅行或用户业务记录。

## 职责和数据

服务运行配置位于 `config/application.json`，可用环境变量覆盖。空间数据按用途分开维护：

| 数据文件 | 内容 | 主要使用方 |
| --- | --- | --- |
| `data/world.pmtiles`、`data/china.pmtiles` | 矢量地图瓦片 | 前端底图 |
| `data/admin_boundaries.gpkg` | 行政区树节点及边界 | 地区识别、范围显示 |
| `data/china_railway_network.gpkg` | 铁路线路及站点空间数据 | 铁路路线与站点检索 |
| `data/airports.gpkg` | 机场点数据 | 航班机场识别 |
| `data/poi.gpkg` | POI 数据与检索索引 | 地点搜索 |

地图数据不放在业务 PostgreSQL 数据库内。新增或替换地图数据时，遵照对应导入工具和配置更新；不要删除尚未确认可重建的数据文件。原始导入数据位于 `data/source/`（若存在），只在重建数据时需要。

## 安装与启动

要求 Python 3.11 或兼容版本：

```powershell
cd mapserver
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe main.py
```

默认地址为 `http://127.0.0.1:8765`，接口文档为 `/docs`。常用配置包括 `MAP_HOST`、`MAP_PORT`、`MAP_DATA_DIR`、`MAP_CONFIG`，以及各数据文件路径环境变量；完整配置以 `config/application.json` 和 `app/settings.py` 为准。

## 接口范围

主要接口包括 `/api/tiles`、`/api/regions`（`/reverse`、`/reverse/batch`、`/search`、`/{node_id}`、`/boundaries`）。行政区详情、搜索和经纬度反查统一返回祖先到目标节点的 `path` 数组。其他接口包括 `/api/pois`、`/api/airports` 和 `/api/railway`。mapserver 不负责车票、航班、足迹的 CRUD；这些记录属于 `travel` 模块。

## 数据来源与署名

部分底图及 POI 数据来源于 OpenStreetMap。再分发时遵守相应数据许可并保留 OpenStreetMap 署名。具体来源、许可和更新时间应与随数据提供的说明一并维护。
