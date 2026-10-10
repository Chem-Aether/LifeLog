# 日记服务

日记服务是独立的 Spring Boot 模块，预留日记业务边界，使用 PostgreSQL `diary`。

## 当前状态

- 默认端口：`8085`
- 启动类：`com.lifelog.diary.DiaryServiceApplication`
- 当前仅实现 `GET /diary/status` 状态接口；日记 CRUD、查询和媒体关联尚未实现。
- 数据库创建脚本：`database/init.sql`（仅在数据库不存在时创建）；`diary_entry` 表结构仅建立数据库基础，不代表完整日记功能已经交付。

服务启动时通过 Spring SQL 初始化执行 `src/main/resources/db/schema.sql`。建库脚本不会删除已有数据库或数据。服务可独立启动，不依赖账单或旅行服务。开发日记业务时，接口、表结构和规则均由本服务负责；图片由 mediaserver 提供，日记库只应保存媒体服务返回的稳定 ID。
