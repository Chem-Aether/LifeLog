# LifeLog 桌面端

Electron 主进程负责检查 PostgreSQL、按配置顺序启动本地服务并等待端口就绪，然后在 Electron 窗口内加载前端。开发模式使用 Vite；安装包从本地打包的 Vue 静态文件启动，不再依赖外部网页。

## 本地预览

在 `desktop` 目录安装依赖并启动：

```bash
npm install
npm run dev
```

脚本会启动 `font` 下的 Vite 开发服务，随后 Electron 检查 PostgreSQL 并按 `config.json` 启动服务。关闭桌面窗口时，Electron 会结束自己启动的子进程；已预先运行的服务不会被关闭。

PostgreSQL 仍需预先安装、初始化并运行，旅行库需要 PostGIS。Go、Java、Maven 和 Python 运行时也需要在开发机可用。服务日志写入 Electron 的用户数据目录 `logs`。

## Windows 安装包

在 `desktop` 目录运行：

```bash
npm run dist:win
```

打包流程会先构建 `font` 前端，Electron 安装包将页面放入本地 `resources/web`，启动时通过本机静态服务器提供，支持 Vue history 路由。

`config.json` 中定义了开发机服务命令、启动顺序和端口。生产包会从 `desktop/runtime` 收集服务运行文件；Go 地图数据约 7.6 GB，运行时和数据文件尚未自动构建/收集。缺少任一文件时，打包前检查会停止，避免生成不能启动的安装包。PostgreSQL/PostGIS 仍是外部依赖，因此当前还不是面向普通用户的完全自包含发行版。
