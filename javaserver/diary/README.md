# 日记服务

日记服务独立负责图文日记、日期筛选、标签和年度概览，数据存储在 PostgreSQL `diary`，图片文件由 mediaserver 管理，日记数据库只保存稳定的媒体 ID。日记属于当前 LifeLog 空间，不在业务表中重复保存登录用户 ID。

## 启动与配置

- 默认端口：`8085`
- 启动类：`com.lifelog.diary.DiaryServiceApplication`
- 数据库创建脚本：`database/init.sql`
- 表结构：`src/main/resources/db/schema.sql`，服务启动时自动执行
- 可配置项：`DIARY_PORT`、`DIARY_DB_URL`、`DIARY_DB_USERNAME`、`DIARY_DB_PASSWORD`、`JWT_SECRET`

## 接口

- `GET /diary/status`：服务状态
- `GET /diary/entries`：分页查询，支持 `keyword`、`year`、`page`、`size`
- `GET /diary/entries/{id}`：读取单篇日记
- `POST /diary/entries`：创建日记
- `PUT /diary/entries/{id}`：更新日记
- `DELETE /diary/entries/{id}`：删除日记及其标签/媒体关联
- `GET /diary/years`：已有日记年份
- `GET /diary/summary?year=2026`：年度篇数、记录天数及本月篇数

写接口与读取接口均通过公共 JWT 过滤器鉴权。图片先由前端上传至 mediaserver 的 `category=diary`，再将返回的 `media_id` 随日记内容保存。删除日记只清除关联，不会自动删除媒体文件，避免误删其他内容仍在引用的媒体。
