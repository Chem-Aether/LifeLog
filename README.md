# LifeLog

LifeLog 是个人使用的生活记录系统，包含旅行足迹、账单、日记和地图媒体能力。项目采用单仓库、多独立服务结构，各服务可按需启动和部署。

| 目录/服务 | 职责 | 默认端口 |
| --- | --- | ---: |
| `font/` | Vue 3 前端 | 5173 |
| `javaserver/auth/` | 登录、注册、验证码和 JWT 签发 | 8081 |
| `javaserver/bill/` | 账户、分类、流水和账单统计 | 8083 |
| `javaserver/travel/` | 铁路、航班、足迹和旅行数据导入导出 | 8084 |
| `javaserver/diary/` | 日记服务入口；当前仅有状态接口 | 8085 |
| `mapserver/` | 离线地图、行政区、机场、POI、铁路空间数据和地理接口 | 8765 |
| `spyderserver/` | 在线查询 12306 车次经停信息 | 8082 |
| `mediaserver/` | 图片上传、访问和删除 | 8780 |

Java 服务各自运行进程并连接独立 PostgreSQL 数据库。`javaserver/` 只是 Maven 聚合构建目录，`common` 是被 Java 服务引用的公共库，不是独立服务。Python 服务也独立运行。

## 从哪里开始

- [开发指南](docs/开发文档.md)：系统关系、启动顺序、数据库初始化和开发约定。
- [Java 服务总览](javaserver/README.md)：Maven 多模块结构和 Java 服务启动方式。
- 各服务目录中的 `README.md`：该服务的职责、依赖、接口和配置。

只开发某项功能时，启动对应服务及其必要依赖即可。旅行轨迹需要 `mapserver`；自动查询车次时才额外需要 `spyderserver`。媒体上传需要 `mediaserver`。
