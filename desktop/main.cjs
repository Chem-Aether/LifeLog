const { app, BrowserWindow, Menu, dialog, shell } = require('electron')
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const http = require('node:http')
const net = require('node:net')
const path = require('node:path')

const config = require('./config.json')
const children = []
let windowRef
let frontendServer
let frontendUrl
let frontendLog
let shuttingDown = false

const hasSingleInstanceLock = app.requestSingleInstanceLock()
if (!hasSingleInstanceLock) app.quit()

const startupPage = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LifeLog 启动中</title><style>
*{box-sizing:border-box}body{margin:0;min-height:100vh;background:#f3f6f8;color:#263943;font:15px/1.6 "Microsoft YaHei",sans-serif;display:grid;place-items:center}.panel{width:min(520px,90vw);padding:36px 42px;background:white;border:1px solid #dce5e8;border-radius:8px;box-shadow:0 12px 36px #183b4b12}h1{margin:0 0 4px;font-size:24px}.sub{margin:0 0 28px;color:#71838a}.state{height:8px;background:#e8eff0;border-radius:8px;overflow:hidden}.bar{height:100%;width:4%;background:#238f88;transition:width .3s ease}.status{margin:12px 0;color:#52666e}.steps{padding:0;margin:18px 0 0;list-style:none;display:grid;gap:8px;color:#819096}.steps li{display:flex;gap:10px;align-items:center}.dot{width:8px;height:8px;border-radius:50%;background:#c8d4d7}.steps li.active{color:#238f88}.steps li.active .dot{background:#238f88}.steps li.done{color:#4b8f78}.steps li.done .dot{background:#4b8f78}.error{color:#b54747;white-space:pre-wrap}</style><div class="panel"><h1>LifeLog</h1><p class="sub">正在准备本机服务</p><div class="state"><div class="bar" id="bar"></div></div><p class="status" id="status">正在检查运行环境…</p><ul class="steps" id="steps"></ul></div><script>window.setStartupState=(state)=>{document.getElementById('status').textContent=state.message||'';document.getElementById('bar').style.width=(state.progress||4)+'%';document.getElementById('steps').innerHTML=(state.steps||[]).map(s=>'<li class="'+s.state+'"><span class="dot"></span>'+s.label+'</li>').join('');if(state.error){document.getElementById('status').className='status error';document.getElementById('status').textContent=state.error}}</script></html>`

function createWindow() {
  windowRef = new BrowserWindow({
    width: 1440,
    height: 960,
    minWidth: 900,
    minHeight: 640,
    autoHideMenuBar: true,
    show: false,
    backgroundColor: '#f3f6f8',
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true },
  })
  windowRef.once('ready-to-show', () => windowRef.show())
  windowRef.webContents.on('console-message', (_event, details) => {
    if (frontendLog && details.level >= 2) frontendLog.write(`[console:${details.level}] ${details.message}\n`)
  })
  windowRef.webContents.on('did-fail-load', (_event, code, description, url, isMainFrame) => {
    frontendLog?.write(`[load-failed] code=${code} mainFrame=${isMainFrame} url=${url} error=${description}\n`)
  })
  windowRef.webContents.on('render-process-gone', (_event, details) => {
    frontendLog?.write(`[renderer-gone] reason=${details.reason} exitCode=${details.exitCode}\n`)
  })
  windowRef.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://') || url.startsWith('http://')) shell.openExternal(url)
    return { action: 'deny' }
  })
  windowRef.loadURL(frontendUrl || `data:text/html;charset=utf-8,${encodeURIComponent(startupPage)}`)
}

function setStartupState(message, progress, steps, error) {
  if (!windowRef || windowRef.isDestroyed()) return
  windowRef.webContents.executeJavaScript(`window.setStartupState(${JSON.stringify({ message, progress, steps, error })})`).catch(() => {})
}

function canConnect(host, port, timeoutMs = 700) {
  return new Promise(resolve => {
    const socket = net.createConnection({ host, port })
    const done = result => { socket.destroy(); resolve(result) }
    socket.setTimeout(timeoutMs)
    socket.once('connect', () => done(true))
    socket.once('timeout', () => done(false))
    socket.once('error', () => done(false))
  })
}

async function waitForPort(host, port, timeoutMs, child) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (child && child.spawnError) throw new Error(`启动进程失败：${child.spawnError.message}`)
    if (child && child.exitCode !== null) throw new Error(`进程提前退出，退出码 ${child.exitCode}`)
    if (await canConnect(host, port)) return
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  throw new Error(`等待 ${host}:${port} 就绪超时（${Math.round(timeoutMs / 1000)} 秒）`)
}

function serviceCommand(service) {
  if (app.isPackaged) {
    const executable = service.packagedExecutable
      ? path.join(process.resourcesPath, service.packagedExecutable)
      : service.command
    const cwd = service.packagedCwd
      ? path.join(process.resourcesPath, service.packagedCwd)
      : path.resolve(__dirname, service.cwd || '.')
    return { command: executable, args: service.packagedArgs || [], cwd }
  }
  return { command: service.command, args: service.args || [], cwd: path.resolve(__dirname, service.cwd || '.') }
}

function openServiceLog(id) {
  const dir = path.join(app.getPath('userData'), 'logs')
  fs.mkdirSync(dir, { recursive: true })
  return fs.createWriteStream(path.join(dir, `${id}.log`), { flags: 'a' })
}

async function startService(service) {
  const host = service.host || '127.0.0.1'
  if (await canConnect(host, service.port)) return 'already running'
  const target = serviceCommand(service)
  if (app.isPackaged && service.packagedExecutable && !fs.existsSync(target.command)) {
    throw new Error(`安装包缺少模块运行文件：${target.command}`)
  }

  const log = openServiceLog(service.id)
  const child = spawn(target.command, target.args, {
    cwd: target.cwd,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: process.platform === 'win32' && /\.cmd$/i.test(target.command),
  })
  child.stdout.pipe(log, { end: false })
  child.stderr.pipe(log, { end: false })
  child.spawnError = null
  child.once('error', error => {
    child.spawnError = error
    log.write(`\n[launcher] ${error.stack || error}\n`)
  })
  child.once('exit', (code, signal) => log.write(`\n[launcher] exited code=${code} signal=${signal}\n`))
  children.push({ child, log, id: service.id })
  await waitForPort(host, service.port, service.timeoutMs || 90000, child)
  return 'started'
}

async function startServices() {
  const steps = [{ label: 'PostgreSQL 数据库', state: 'active' }, ...config.services.map(service => ({ label: service.name, state: 'pending' }))]
  setStartupState('检查本机 PostgreSQL…', 5, steps)
  if (!await canConnect(config.database.host, config.database.port, 1500)) {
    throw new Error(`连接不到 PostgreSQL（${config.database.host}:${config.database.port}）。请先启动数据库服务。`)
  }
  steps[0].state = 'done'
  for (let index = 0; index < config.services.length; index += 1) {
    const service = config.services[index]
    steps[index + 1].state = 'active'
    const progress = Math.round(8 + (index / config.services.length) * 82)
    setStartupState(`正在启动${service.name}…`, progress, steps)
    const result = await startService(service)
    steps[index + 1].state = 'done'
    setStartupState(`${service.name}${result === 'started' ? '已就绪' : '已在运行'}`, progress + 5, steps)
  }
}

const contentTypes = {
  '.css': 'text/css; charset=utf-8', '.html': 'text/html; charset=utf-8', '.ico': 'image/x-icon',
  '.jpeg': 'image/jpeg', '.jpg': 'image/jpeg', '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.png': 'image/png', '.svg': 'image/svg+xml',
  '.webp': 'image/webp', '.woff': 'font/woff', '.woff2': 'font/woff2',
}

async function serveFrontend() {
  if (!app.isPackaged) return config.frontend.developmentUrl
  const root = path.join(process.resourcesPath, 'web')
  if (!fs.existsSync(path.join(root, 'index.html'))) throw new Error('安装包中缺少前端页面，请重新构建安装包。')
  const logDir = path.join(app.getPath('userData'), 'logs')
  fs.mkdirSync(logDir, { recursive: true })
  frontendLog = fs.createWriteStream(path.join(logDir, 'frontend.log'), { flags: 'a' })
  frontendServer = http.createServer((request, response) => {
    let pathname
    try { pathname = decodeURIComponent(new URL(request.url, 'http://127.0.0.1').pathname) } catch { response.writeHead(400).end(); return }
    const requested = path.resolve(root, `.${pathname}`)
    if (requested !== root && !requested.startsWith(`${root}${path.sep}`)) { response.writeHead(403).end(); return }
    const exists = fs.existsSync(requested) && fs.statSync(requested).isFile()
    const isAssetRequest = pathname.startsWith('/assets/') || path.extname(pathname) !== ''
    if (!exists && isAssetRequest) {
      response.writeHead(404).end()
      frontendLog?.write(`[asset] 404 ${pathname}\n`)
      return
    }
    const filePath = exists ? requested : path.join(root, 'index.html')
    response.setHeader('Cache-Control', path.basename(filePath) === 'index.html' ? 'no-store' : 'public, max-age=31536000, immutable')
    response.setHeader('Content-Type', contentTypes[path.extname(filePath).toLowerCase()] || 'application/octet-stream')
    response.on('finish', () => {
      if (pathname.startsWith('/assets/')) frontendLog?.write(`[asset] ${response.statusCode} ${response.getHeader('Content-Type')} ${pathname}\n`)
    })
    fs.createReadStream(filePath).pipe(response)
  })
  await new Promise((resolve, reject) => {
    frontendServer.once('error', reject)
    frontendServer.listen(config.frontend.port, '127.0.0.1', resolve)
  })
  return `http://127.0.0.1:${config.frontend.port}/`
}

async function shutdown() {
  if (shuttingDown) return
  shuttingDown = true
  if (frontendServer) frontendServer.close()
  frontendLog?.end()
  for (const entry of children.reverse()) {
    if (entry.child.exitCode === null) {
      if (process.platform === 'win32' && entry.child.pid) {
        await new Promise(resolve => {
          const killer = spawn('taskkill.exe', ['/pid', String(entry.child.pid), '/t', '/f'], { windowsHide: true, stdio: 'ignore' })
          killer.once('exit', resolve)
          killer.once('error', resolve)
        })
      } else entry.child.kill('SIGTERM')
    }
    entry.log.end()
  }
}

app.whenReady().then(async () => {
  if (!hasSingleInstanceLock) return
  app.setName('LifeLog')
  Menu.setApplicationMenu(null)
  createWindow()
  try {
    await startServices()
    setStartupState('正在加载 LifeLog…', 96, config.services.map(service => ({ label: service.name, state: 'done' })))
    frontendUrl = await serveFrontend()
    await windowRef.webContents.session.clearCache()
    await windowRef.loadURL(frontendUrl)
  } catch (error) {
    setStartupState('启动失败', 100, [], error.message)
    dialog.showErrorBox('LifeLog 启动失败', `${error.message}\n\n详细服务日志位于：${path.join(app.getPath('userData'), 'logs')}`)
    await shutdown()
    app.quit()
  }
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow() })
})

app.on('before-quit', event => {
  if (!shuttingDown) {
    event.preventDefault()
    shutdown().finally(() => app.quit())
  }
})

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit() })
