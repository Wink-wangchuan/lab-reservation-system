// LabWise 桌面客户端主进程
//
// 形态：NSIS 安装包 + Electron 壳，直连 tailnet 服务器（可被 LABWISE_SERVER 覆盖）。
// 首次启动必须先同意《用户协议与隐私说明》；同意结果存 userData/client-state.json。
const { app, BrowserWindow, Menu, shell, ipcMain } = require('electron')
const path = require('path')
const fs = require('fs')
const { initUpdater, checkForUpdate } = require('./updater')

const SERVER_URL = (process.env.LABWISE_SERVER || 'http://100.96.0.100:21031').replace(/\/+$/, '')
// 更新接口在 21030 后端；与 SERVER_URL 分开是为了让前端页面与 API 地址可独立覆盖
const API_URL = (process.env.LABWISE_API || 'http://100.96.0.100:21030').replace(/\/+$/, '')
const STATE_FILE = () => path.join(app.getPath('userData'), 'client-state.json')
/** 打包/部署前的自检模式：强制走协议页并自动同意，验证能进入服务器页面 */
const SELFTEST = process.env.LABWISE_SELFTEST === '1'

let mainWindow = null
let splashWindow = null
/** 启动画面是否已结束；主界面要等它结束才显示，避免两窗重叠 */
let splashFinished = false
/** 主窗口内容是否已可显示 */
let mainReadyToShow = false
/** 启动画面最长展示时间，视频异常时兜底 */
const SPLASH_TIMEOUT_MS = 10000
/** 自检模式与显式关闭时跳过启动画面 */
const SPLASH_ENABLED = !SELFTEST && process.env.LABWISE_NO_SPLASH !== '1'

function readState() {
  try {
    return JSON.parse(fs.readFileSync(STATE_FILE(), 'utf8'))
  } catch {
    return {}
  }
}

function writeState(state) {
  fs.writeFileSync(STATE_FILE(), JSON.stringify(state, null, 2), 'utf8')
}

function loadApp() {
  if (!mainWindow) return
  mainWindow.loadURL(SERVER_URL).catch(() => {
    mainWindow.loadFile('error.html', { query: { url: SERVER_URL } })
  })
}

function openAgreement() {
  const win = new BrowserWindow({
    width: 760,
    height: 720,
    title: '用户协议与隐私说明',
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })
  win.loadFile('eula.html', { query: { readonly: '1' } })
}

function buildMenu() {
  const template = [
    {
      label: '应用',
      submenu: [
        { label: '重新加载', accelerator: 'F5', click: () => mainWindow?.webContents.reload() },
        { label: '返回登录页', click: () => mainWindow?.loadURL(`${SERVER_URL}/login`) },
        { type: 'separator' },
        { label: '用户协议与隐私说明', click: openAgreement },
        { type: 'separator' },
        { role: 'quit', label: '退出' }
      ]
    },
    {
      label: '帮助',
      submenu: [
        { label: '检查更新…', click: () => checkForUpdate({ manual: true }) },
        { type: 'separator' },
        {
          label: '服务器地址',
          click: () => {
            const { dialog } = require('electron')
            dialog.showMessageBox(mainWindow, {
              type: 'info',
              title: '服务器地址',
              message: `当前连接：${SERVER_URL}`,
              detail: '如需切换，请设置环境变量 LABWISE_SERVER 后重启客户端。'
            })
          }
        },
        { label: '关于', click: () => shell.openExternal('https://github.com/Wink-wangchuan/lab-reservation-system') }
      ]
    }
  ]
  Menu.setApplicationMenu(Menu.buildFromTemplate(template))
}

function mainShowIfReady() {
  if (!mainReadyToShow || !splashFinished) return
  if (mainWindow && !mainWindow.isDestroyed() && !mainWindow.isVisible()) mainWindow.show()
}

/** 关闭启动画面并放出主窗口（可重复调用） */
function finishSplash() {
  if (splashFinished) return
  splashFinished = true
  if (splashWindow && !splashWindow.isDestroyed()) splashWindow.destroy()
  splashWindow = null
  mainShowIfReady()
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.focus()
}

/** 启动画面：无边框小窗播放 4:3 品牌动画，播放结束/失败/点击后进入主界面 */
function createSplashWindow() {
  splashWindow = new BrowserWindow({
    width: 720,
    height: 540,
    useContentSize: true,
    frame: false,
    resizable: false,
    maximizable: false,
    fullscreenable: false,
    show: false,
    skipTaskbar: true,
    backgroundColor: '#dfe4f6',
    webPreferences: {
      preload: path.join(__dirname, 'splash-preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })
  ipcMain.once('splash:done', finishSplash)
  splashWindow.once('ready-to-show', () => splashWindow?.show())
  // 用户直接关掉启动窗（Alt+F4）时也要把主界面放出来
  splashWindow.once('closed', finishSplash)
  splashWindow.loadFile('splash.html')
  setTimeout(finishSplash, SPLASH_TIMEOUT_MS)
}

function createWindow() {
  mainReadyToShow = false
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 1024,
    minHeight: 700,
    title: 'LabWise 智慧实验室预约管理系统',
    icon: path.join(__dirname, 'icon.png'),
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  mainWindow.once('ready-to-show', () => {
    mainReadyToShow = true
    mainShowIfReady()
  })

  // 外部链接交给系统浏览器，应用内只允许服务器地址与本地协议页
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })
  mainWindow.webContents.on('will-navigate', (event, url) => {
    const allowed = url.startsWith(SERVER_URL) || url.startsWith('file://')
    if (!allowed) {
      event.preventDefault()
      shell.openExternal(url)
    }
  })

  // 网络错误时回到本地错误页，避免白屏
  mainWindow.webContents.on('did-fail-load', (_e, code, desc, url, isMainFrame) => {
    if (isMainFrame && url.startsWith(SERVER_URL) && code !== -3) {
      mainWindow.loadFile('error.html', { query: { url: SERVER_URL, desc } })
    }
  })

  // 启动后自动检查一次更新（只查一次，避免每次页面刷新都弹窗）
  let autoUpdateChecked = false
  mainWindow.webContents.on('did-finish-load', () => {
    if (SELFTEST || autoUpdateChecked || !readState().eulaAccepted) return
    if (!mainWindow.webContents.getURL().startsWith(SERVER_URL)) return
    autoUpdateChecked = true
    setTimeout(() => checkForUpdate({ manual: false }), 8000)
  })

  if (!SELFTEST && readState().eulaAccepted) {
    loadApp()
  } else {
    mainWindow.loadFile('eula.html')
  }

  if (SELFTEST) {
    let accepted = false
    mainWindow.webContents.on('did-finish-load', async () => {
      const url = mainWindow.webContents.getURL()
      if (url.includes('eula.html') && !accepted) {
        accepted = true
        const labels = await mainWindow.webContents.executeJavaScript(
          '({ title: document.querySelector("h1")?.textContent, content: document.querySelector("#paper")?.textContent.length })')
        console.log(`SELFTEST_EULA ${JSON.stringify(labels)}`)
        await mainWindow.webContents.executeJavaScript(
          'document.querySelector("#agree").checked = true;' +
          'document.querySelector("#agree").dispatchEvent(new Event("change"));' +
          'document.querySelector("#accept").click(); true')
        return
      }
      if (url.startsWith(SERVER_URL)) {
        console.log(`SELFTEST_OK ${url}`)
        setTimeout(() => app.exit(0), 800)
      }
    })
    setTimeout(() => {
      console.error('SELFTEST_TIMEOUT')
      app.exit(2)
    }, 30000)
  }
}

ipcMain.handle('eula:accept', () => {
  writeState({ ...readState(), eulaAccepted: true, acceptedAt: new Date().toISOString() })
  loadApp()
})

ipcMain.handle('eula:decline', () => {
  app.quit()
})

ipcMain.handle('app:retry', () => {
  loadApp()
})

ipcMain.handle('app:check-update', () => checkForUpdate({ manual: true }))

app.whenReady().then(() => {
  initUpdater({
    apiBase: API_URL,
    getMainWindow: () => mainWindow,
    getState: readState,
    setState: writeState
  })
  buildMenu()
  if (SPLASH_ENABLED) {
    createSplashWindow()
  } else {
    splashFinished = true
  }
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
