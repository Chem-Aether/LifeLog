## 架构

日记和旅行 Java 服务负责业务记录及媒体上传、删除；SeaweedFS 通过 S3 接口保存文件；数据库只存对象相对路径；前端从 `VITE_MEDIA_BASE_URL` 读取访问地址。服务之间没有互相查询业务数据，但 Java 仍依赖 S3 协议和配置，这是必要的存储依赖。SeaweedFS 内部由 Master 管卷位置，Volume 存文件内容，Filer 管路径和文件元数据，S3 网关提供兼容接口。[SeaweedFS 架构说明](https://github.com/seaweedfs/seaweedfs/blob/master/README.md?plain=1)

你安装的是 `4.48 enterprise`。目前运行的是 `weed mini` 单机模式，数据持久化在 `C:\Database\SeaweedFS\data`。这个模式适合开发和家庭单机使用，不提供多节点高可用。

**常用入口**

| 地址 | 用途 |
| --- | --- |
| `http://127.0.0.1:9333` | Master 状态和卷信息 |
| `http://127.0.0.1:8888` | Filer 文件管理界面 |
| `http://127.0.0.1:8333` | S3 接口，不是管理网页 |
| `http://127.0.0.1:23646` | Admin UI，目前启动参数将它关闭了 |

**启动和停止**

前台启动时可以在 PowerShell 执行，窗口里按 `Ctrl+C` 停止：

```powershell
& "C:\Database\SeaweedFS\weed.exe" mini -dir="C:\Database\SeaweedFS\data" -ip=127.0.0.1 -ip.bind=127.0.0.1 -bucket=lifelog-media -webdav=false -admin.ui=false
```

目前服务是隐藏运行的。需要停止时，可以查找占用 S3 端口的进程再停止：

```powershell
$seaweedPid = (Get-NetTCPConnection -LocalPort 8333 -State Listen | Select-Object -First 1 -ExpandProperty OwningProcess)
Stop-Process -Id $seaweedPid
```

**查看状态和桶**

进入管理命令行：

```powershell
& "C:\Database\SeaweedFS\weed.exe" shell -master=127.0.0.1:9333 -filer=127.0.0.1:8888
```

进入后输入：

```text
help
volume.list
s3.bucket.list
```

`volume.list` 查看卷和占用情况，`s3.bucket.list` 查看桶。SeaweedFS 官方 shell 也提供 `volume.list` 等拓扑管理命令。[命令实现](https://github.com/seaweedfs/seaweedfs/blob/master/weed/shell/command_volume_list.go)

不要手动删除数据目录里的 `.dat`、`.idx` 等文件，也不要把它们当成普通图片文件改名。SeaweedFS 维护文件内容和索引的对应关系，日常管理用 Filer 或 S3 工具。

**备份要点**

完整备份需要同时备份 PostgreSQL 和整个 SeaweedFS 数据目录。最简单的单机做法是先停止 SeaweedFS，再复制 `C:\Database\SeaweedFS\data` 到另一块磁盘；数据库另用 `pg_dump` 备份。恢复时两部分都要恢复，因为数据库里的媒体相对路径需要对应的对象文件仍然存在。

**手机访问提醒**

目前绑定地址是 `127.0.0.1`，只有这台电脑能访问，手机不能通过这个地址读图。要让家庭局域网设备访问，需要把服务绑定到电脑的局域网地址，并把 `VITE_MEDIA_BASE_URL` 改成类似 `http://192.168.1.20:8333`，同时在 Windows 防火墙中仅允许家庭网络访问 8333 端口。

另一个重要点是当前 Java 服务设置了公开读取策略。任何能访问 SeaweedFS 且知道媒体路径的人都能读取文件，所以不要把 8333 直接暴露到公网；如果需要媒体私密性，之后应改成签名访问或由后端鉴权转发。


## 运维命令

下面这些命令按你现在的配置编写，数据目录是 `C:\Database\SeaweedFS\data`。启动前先查状态，避免重复启动。

**查看状态**

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8333,8888,9333,9340 -ErrorAction SilentlyContinue |
  Select-Object LocalPort, OwningProcess
```

端口对应关系：`8333` 是 S3，`8888` 是 Filer，`9333` 是 Master，`9340` 是 Volume。

检查 S3 端口是否可连接：

```powershell
Test-NetConnection 127.0.0.1 -Port 8333
```

打开管理页面：

```powershell
Start-Process http://127.0.0.1:9333
Start-Process http://127.0.0.1:8888
```

**隐藏窗口启动**

```powershell
Start-Process -FilePath "C:\Database\SeaweedFS\weed.exe" -ArgumentList @("mini", "-dir=C:\Database\SeaweedFS\data", "-ip=127.0.0.1", "-ip.bind=127.0.0.1", "-bucket=lifelog-media", "-webdav=false", "-admin.ui=false") -WorkingDirectory "C:\Database\SeaweedFS" -WindowStyle Hidden
```

**关闭隐藏进程**

下面会先按可执行文件和数据目录定位进程，只匹配到一个时才停止：

```powershell
$matches = @(Get-CimInstance Win32_Process -Filter "name = 'weed.exe'" |
  Where-Object {
    $_.ExecutablePath -eq "C:\Database\SeaweedFS\weed.exe" -and
    $_.CommandLine.Contains("-dir=C:\Database\SeaweedFS\data")
  })

if ($matches.Count -eq 1) {
  Stop-Process -Id $matches[0].ProcessId
} elseif ($matches.Count -eq 0) {
  Write-Output "SeaweedFS 未运行"
} else {
  Write-Output "找到多个匹配进程，请先检查进程列表，不要批量停止"
  $matches | Select-Object ProcessId, CommandLine
}
```

如果是在当前 PowerShell 窗口前台运行，直接按 `Ctrl+C` 关闭。

**重启**

先执行上面的关闭命令，确认端口释放后，再执行隐藏窗口启动命令。

**查看桶和卷**

```powershell
& "C:\Database\SeaweedFS\weed.exe" shell -master=127.0.0.1:9333 -filer=127.0.0.1:8888
```

进入命令行后输入：

```text
s3.bucket.list
volume.list
help
```

不要删除 `C:\Database\SeaweedFS\data` 来关闭服务。该目录保存对象数据和索引，删除它会导致媒体丢失。