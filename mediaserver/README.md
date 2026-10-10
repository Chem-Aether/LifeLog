# 媒体服务

媒体服务是独立的 Python/FastAPI 文件服务，负责图片上传、读取和删除。业务服务保存稳定的 `media_id`，不保存本机绝对路径；媒体 URL 的主机地址可在部署时变更而不改业务记录。

## 安装与启动

```powershell
cd mediaserver
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe main.py
```

默认地址为 `http://127.0.0.1:8780`，接口文档为 `/docs`。配置文件为 `config/application.json`，可通过 `MEDIA_CONFIG`、`MEDIA_DATA_DIR`、`MEDIA_HOST`、`MEDIA_PORT`、`MEDIA_PUBLIC_BASE_URL` 等环境变量覆盖。

## 接口与限制

- `POST /api/media`：以 multipart 上传图片，可传分类 `travel`、`diary`、`bill` 或 `shared`。
- `GET /media/{media_id}`：通过稳定 ID 获取媒体文件。
- `DELETE /api/media/{media_id}`：删除媒体记录和文件。
- `GET /health`：健康检查。

默认接受 JPEG、PNG、WebP、GIF，单文件上限 20 MiB；拒绝 SVG 等可执行或主动内容格式。配置 `MEDIA_API_KEY` 后，写入接口需要通过 `X-Media-Key` 提供密钥。

## 存储、迁移与安全

文件按类别/年/月归档，元数据 SQLite 与各类别文件目录需要一起备份。生产迁移时复制元数据库及所有实际挂载的类别目录，保持 `media_id` 不变；更换公开域名只调整 `MEDIA_PUBLIC_BASE_URL`。可以用 `MEDIA_STORAGE_TRAVEL`、`MEDIA_STORAGE_DIARY`、`MEDIA_STORAGE_BILL`、`MEDIA_STORAGE_SHARED` 将类别挂载到不同磁盘。

不要将未受保护的上传、删除接口直接暴露到公网；应配置密钥并放在 HTTPS 反向代理之后。媒体服务与地图服务、爬虫服务及 Java 业务数据库相互独立。
