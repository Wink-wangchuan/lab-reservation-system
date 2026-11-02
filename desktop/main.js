// LabWise 桌面客户端主进程
//
// 形态：NSIS 安装包 + Electron 壳，直连 tailnet 服务器（可被 LABWISE_SERVER 覆盖）。
// 首次启动必须先同意《用户协议与隐私说明》；同意结果存 userData/client-state.json。
const { app, BrowserWindow, Menu, shell, ipcMain } = require('electron')
const path = require('path')
const fs = require('fs')
const { initUpdater, checkForUpdate } = require('./updater')

const SERVER_URL = (process.env.LABWISE_SERVER || 'http://100.96.0.100:8092').replace(/\/+$/, '')
// 更新接口在 8091 后端；与 SERVER_URL 分开是为了让前端页面与 API 地址可独立覆盖
const API_URL = (process.env.LABWISE_API || 'http://100.96.0.100:8091').replace(/\/+$/, '')
const STATE_FILE = () => path.join(app.getPath('userData'), 'client-state.json')
/** 打包/部署前的自检模式：强制走协议页并自动同意，验证能进入服务器页面 */
const SELFTEST = process.env.LABWISE_SELFTEST === '1'

let mainWindow = null

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

function createWindow() {
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

  mainWindow.once('ready-to-show', () => mainWindow.show())

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
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
