# 旅行服务

旅行服务管理铁路行程、航班和旅行足迹，关系与空间业务数据存于独立的 PostgreSQL/PostGIS 数据库 `travel`。

## 运行配置

- 默认端口：`8084`
- 启动类：`com.lifelog.travel.TravelServiceApplication`
- 数据库：PostgreSQL/PostGIS `travel`
- 数据库创建脚本：`database/init.sql`（仅在数据库不存在时创建）
- 外部服务地址在 `src/main/resources/application.yml` 配置，可用 `MAP_BASE_URL`、`SPIDER_BASE_URL` 覆盖。

## 服务边界与调用

- 本服务拥有航班、列车、途经站和足迹业务记录，并负责相应 CRUD、里程和导入导出。
- mapserver 提供空间检索、地理编码、行政区及铁路空间数据；编辑或查询旅行数据不等于拥有这些地图数据。
- spyderserver 仅用于在线查询 12306 车次信息，是可选依赖。仅按途经站查询或管理已有记录不需要爬虫服务。
- 媒体文件由 mediaserver 保存；旅行记录如需关联图片只保存稳定的 `media_id`，不保存磁盘路径。

服务启动时通过 Spring SQL 初始化执行 `src/main/resources/db/schema.sql`，创建当前版本所需的表结构。数据库已使用 `location_type` 保存足迹地点类型，不再包含旧字段迁移或兼容逻辑；后续数据库结构变更应通过明确的版本化迁移管理。

足迹行政区缓存首次启用后，可在 PostgreSQL、mapserver 均运行时执行 `python scripts/backfill_region_cache.py`，分批为已有坐标足迹缓存最细行政区 ID。脚本通过 `TRAVEL_DB_URL`、`TRAVEL_DB_USERNAME`、`TRAVEL_DB_PASSWORD` 和 `MAP_BASE_URL` 读取连接配置；缺省值与 `application.yml` 一致，可中断后重复执行。未匹配的记录保持 `region_id` 为空，再次执行时会重试。

## 数据交换

铁路数据支持 GeoPackage，航班和足迹支持表格化导入导出；铁路空间图层可在 QGIS 中使用。导入格式、模板、批量操作及接口参数以开发指南和当前接口定义为准。数据库初始化规则见[开发指南](../../docs/开发文档.md)。
