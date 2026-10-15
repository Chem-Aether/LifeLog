const { app, BrowserWindow, Menu, shell } = require('electron')
const path = require('node:path')

function createWindow() {
  const window = new BrowserWindow({
    width: 1440,
    height: 960,
    minWidth: 900,
    minHeight: 640,
    autoHideMenuBar: true,
    show: false,
    backgroundColor: '#f3f6f8',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  window.once('ready-to-show', () => window.show())
  window.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://') || url.startsWith('http://')) shell.openExternal(url)
    return { action: 'deny' }
  })

  const configuredUrl = process.env.LIFELOG_WEB_URL || require('./config.json').webUrl
  window.loadURL(configuredUrl)
}

app.whenReady().then(() => {
  app.setName('LifeLog')
  Menu.setApplicationMenu(null)
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
