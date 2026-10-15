# LifeLog 桌面端

Electron 桌面壳直接加载现有 Vue 网页，不复制业务界面。浏览器网页和桌面端继续共用同一套前端与后端服务。

## 本地预览

在 `desktop` 目录安装依赖并启动：

```bash
npm install
npm run dev
```

脚本会启动 `font` 下的 Vite 开发服务并打开桌面窗口。关闭桌面窗口或终端即可退出。

## 指向部署站点

修改 `config.json` 的 `webUrl` 为网页部署地址后，运行 `npm start`；也可以通过 `LIFELOG_WEB_URL` 环境变量覆盖该地址。

## Windows 安装包

确认 `config.json` 指向可访问的 LifeLog 网页地址后，在 `desktop` 目录运行：

```bash
npm run dist:win
```

安装包输出到 `desktop/release`。桌面端需要网络访问该网页及其配置的 API 服务；后端地址仍由网页部署配置管理。
