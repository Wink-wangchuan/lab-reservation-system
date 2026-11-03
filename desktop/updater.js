// 桌面端更新模块：服务器优先，GitHub Releases 兜底
//
// 流程：检查版本 -> 弹窗询问 -> 下载安装包（进度窗）-> 校验 SHA-256 -> 调起 NSIS 安装器
// 说明：当前安装的旧版本（1.0.0）还没有本模块，需要手动安装一次 4.5.0 后才能享受应用内更新。
const { app, BrowserWindow, dialog, shell } = require('electron')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')
const http = require('http')
const https = require('https')
const { spawn } = require('child_process')

const REPO = 'Wink-wangchuan/lab-reservation-system'

// 更新清单验签公钥（Ed25519，SPKI PEM）
// 私钥只保存在发布机/服务器的受限路径，服务器被攻破也无法伪造「合法清单」
const UPDATE_PUBLIC_KEY = `-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEADoxMQVLXqJ/ZmD+1F6VnGPxi3bJIQ/i219O1Zdde4Ts=
-----END PUBLIC KEY-----`

let context = {
  apiBase: '',
  getMainWindow: () => null,
  getState: () => ({}),
  setState: () => {}
}
let checking = false

function initUpdater(options) {
  context = { ...context, ...options }
}

/** 版本号按数字段比较（忽略 v 前缀和非数字后缀） */
function isNewer(remote, local) {
  const parse = (value) => String(value || '')
    .replace(/^v/i, '')
    .split('.')
    .map((part) => parseInt(part, 10) || 0)
  const a = parse(remote)
  const b = parse(local)
  for (let i = 0; i < Math.max(a.length, b.length); i += 1) {
    const x = a[i] || 0
    const y = b[i] || 0
    if (x !== y) return x > y
  }
  return false
}

function readResponse(res) {
  return new Promise((resolve, reject) => {
    const chunks = []
    res.on('data', (chunk) => chunks.push(chunk))
    res.on('end', () => resolve(Buffer.concat(chunks)))
    res.on('error', reject)
  })
}

/** 简单 GET（支持重定向），返回响应流 */
function request(url, redirects = 5) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https:') ? https : http
    const req = lib.get(url, { headers: { 'User-Agent': 'LabWise-Desktop', Accept: 'application/json' } }, (res) => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode)) {
        const location = res.headers.location
        res.resume()
        if (!location || redirects <= 0) {
          reject(new Error('重定向异常'))
          return
        }
        resolve(request(new URL(location, url).toString(), redirects - 1))
        return
      }
      if (res.statusCode !== 200) {
        res.resume()
        reject(new Error(`HTTP ${res.statusCode}`))
        return
      }
      resolve(res)
    })
    req.on('error', reject)
    req.setTimeout(15000, () => req.destroy(new Error('请求超时')))
  })
}

async function fetchJson(url) {
  const res = await request(url)
  const body = await readResponse(res)
  return JSON.parse(body.toString('utf8'))
}

/** 服务器更新接口 */
async function fetchFromServer() {
  const body = await fetchJson(`${context.apiBase}/api/app/update?platform=windows`)
  if (!body || body.code !== 200 || !body.data) {
    throw new Error((body && body.message) || '服务器未配置更新信息')
  }
  const data = body.data
  if (!data.version || !data.downloadUrl) {
    throw new Error('服务器更新信息不完整')
  }
  const downloadUrl = /^https?:\/\//i.test(data.downloadUrl)
    ? data.downloadUrl
    : `${context.apiBase}${data.downloadUrl.startsWith('/') ? '' : '/'}${data.downloadUrl}`
  return {
    version: String(data.version),
    notes: String(data.notes || '').trim(),
    size: Number(data.size) || 0,
    sha256: String(data.sha256 || '').trim(),
    fileName: String(data.fileName || '').trim(),
    signature: String(data.signature || '').trim(),
    url: downloadUrl,
    fallbackUrl: String(data.fallbackUrl || '').trim(),
    source: 'server'
  }
}

/** GitHub Releases 兜底 */
async function fetchFromGithub() {
  const release = await fetchJson(`https://api.github.com/repos/${REPO}/releases/latest`)
  const version = String(release.tag_name || '').replace(/^v/i, '')
  const asset = (release.assets || []).find((item) => /\.exe$/i.test(item.name))
  if (!version || !asset) {
    throw new Error('GitHub 暂无可用安装包')
  }
  return {
    version,
    notes: String(release.body || '').trim(),
    size: Number(asset.size) || 0,
    sha256: '',
    fileName: String(asset.name || '').trim(),
    signature: '',
    url: asset.browser_download_url,
    fallbackUrl: '',
    source: 'github'
  }
}

function showMessage(options) {
  const win = context.getMainWindow()
  return win ? dialog.showMessageBox(win, options) : dialog.showMessageBox(options)
}

/** 下载文件，onProgress(received, total) */
function downloadFile(url, dest, onProgress, redirects = 5) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https:') ? https : http
    const req = lib.get(url, { headers: { 'User-Agent': 'LabWise-Desktop' } }, (res) => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode)) {
        const location = res.headers.location
        res.resume()
        if (!location || redirects <= 0) {
          reject(new Error('下载重定向异常'))
          return
        }
        resolve(downloadFile(new URL(location, url).toString(), dest, onProgress, redirects - 1))
        return
      }
      if (res.statusCode !== 200) {
        res.resume()
        reject(new Error(`下载失败 HTTP ${res.statusCode}`))
        return
      }
      const total = Number(res.headers['content-length']) || 0
      const out = fs.createWriteStream(dest)
      let received = 0
      res.on('data', (chunk) => {
        received += chunk.length
        onProgress(received, total)
      })
      res.on('error', reject)
      out.on('error', reject)
      out.on('finish', () => resolve({ received, total }))
      res.pipe(out)
    })
    req.on('error', reject)
    req.setTimeout(30000, () => req.destroy(new Error('下载超时')))
  })
}

function sha256File(file) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256')
    fs.createReadStream(file)
      .on('data', (chunk) => hash.update(chunk))
      .on('end', () => resolve(hash.digest('hex')))
      .on('error', reject)
  })
}

/** 校验服务器清单签名：version|sha256|fileName|size 的 Ed25519 签名 */
function verifyManifest(info) {
  if (!info.signature) {
    throw new Error('更新清单未签名，已拒绝安装')
  }
  const payload = `${info.version}|${info.sha256}|${info.fileName}|${info.size}`
  try {
    return crypto.verify(
      null,
      Buffer.from(payload, 'utf8'),
      crypto.createPublicKey(UPDATE_PUBLIC_KEY),
      Buffer.from(info.signature, 'base64')
    )
  } catch {
    return false
  }
}

function createProgressWindow(version) {
  const win = new BrowserWindow({
    width: 460,
    height: 220,
    parent: context.getMainWindow() || undefined,
    modal: true,
    resizable: false,
    minimizable: false,
    maximizable: false,
    closable: false,
    title: '正在下载更新',
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'update-preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })
  win.setMenuBarVisibility(false)
  win.loadFile('update.html', { query: { version } })
  let ready = false
  win.webContents.once('did-finish-load', () => {
    ready = true
  })
  return {
    win,
    send(received, total) {
      if (!ready) return
      const percent = total > 0 ? Math.min(100, Math.round((received * 100) / total)) : 0
      win.webContents.send('update:progress', { percent, received, total })
    }
  }
}

/** 询问是否更新；返回 0=立即更新 1=稍后 2=忽略 */
async function askUpdate(info) {
  const sizeText = info.size > 0 ? `安装包大小：${(info.size / 1024 / 1024).toFixed(1)} MB\n` : ''
  const sourceText = info.source === 'server' ? '内网服务器' : 'GitHub Releases'
  const result = await showMessage({
    type: 'info',
    title: '发现新版本',
    message: `发现新版本 v${info.version}`,
    detail: `${info.notes || '建议更新到最新版本。'}\n\n${sizeText}更新来源：${sourceText}`,
    buttons: ['立即更新', '稍后提醒', '忽略此版本'],
    defaultId: 0,
    cancelId: 1,
    noLink: true
  })
  return result.response
}

async function downloadAndInstall(info) {
  const dir = path.join(app.getPath('temp'), 'LabWise-Update')
  fs.mkdirSync(dir, { recursive: true })
  const dest = path.join(dir, `LabWise-Setup-${info.version}.exe`)
  const progress = createProgressWindow(info.version)
  try {
    try {
      await downloadFile(info.url, dest, progress.send)
    } catch (firstError) {
      if (!info.fallbackUrl) throw firstError
      console.warn('[更新] 服务器下载失败，回退 GitHub：', firstError.message)
      await downloadFile(info.fallbackUrl, dest, progress.send)
    }
    if (info.sha256) {
      const actual = await sha256File(dest)
      if (actual.toLowerCase() !== info.sha256.toLowerCase()) {
        fs.rmSync(dest, { force: true })
        throw new Error('安装包校验失败，已删除下载文件')
      }
    }
    // 服务器清单必须验签通过：SHA-256 与清单同源，单独使用时服务器被攻破即可同时篡改
    if (info.source === 'server' && !verifyManifest(info)) {
      fs.rmSync(dest, { force: true })
      throw new Error('更新清单签名校验失败，已删除下载文件')
    }
  } catch (e) {
    progress.win.destroy()
    throw e
  }
  progress.win.destroy()

  const confirm = await showMessage({
    type: 'question',
    title: '下载完成',
    message: `v${info.version} 安装包已下载完成`,
    detail: '安装程序启动后，请按提示完成安装；安装完成后重新打开客户端即可。',
    buttons: ['立即安装', '稍后安装'],
    defaultId: 0,
    cancelId: 1,
    noLink: true
  })
  if (confirm.response !== 0) {
    await showMessage({
      type: 'info',
      title: '安装包已保留',
      message: '可以稍后手动安装',
      detail: dest
    })
    return
  }
  const child = spawn(dest, [], { detached: true, stdio: 'ignore', windowsHide: false })
  child.unref()
  setTimeout(() => app.quit(), 800)
}

async function checkForUpdate({ manual = false } = {}) {
  if (checking) {
    return { status: 'busy' }
  }
  checking = true
  try {
    let info = null
    try {
      info = await fetchFromServer()
    } catch (e) {
      console.warn('[更新] 服务器检查失败：', e.message)
    }
    if (!info) {
      try {
        info = await fetchFromGithub()
      } catch (e) {
        console.warn('[更新] GitHub 检查失败：', e.message)
      }
    }
    if (!info) {
      if (manual) {
        await showMessage({
          type: 'warning',
          title: '检查更新',
          message: '暂时无法获取更新信息',
          detail: '服务器与 GitHub 均不可用，请稍后重试。'
        })
      }
      return { status: 'unavailable' }
    }

    if (!isNewer(info.version, app.getVersion())) {
      if (manual) {
        await showMessage({
          type: 'info',
          title: '检查更新',
          message: `当前已是最新版本 v${app.getVersion()}`
        })
      }
      return { status: 'latest' }
    }

    const state = context.getState() || {}
    if (!manual && state.ignoredUpdateVersion === info.version) {
      return { status: 'ignored' }
    }

    const choice = await askUpdate(info)
    if (choice === 2) {
      context.setState({ ...state, ignoredUpdateVersion: info.version })
      return { status: 'ignored' }
    }
    if (choice !== 0) {
      return { status: 'later' }
    }

    await downloadAndInstall(info)
    return { status: 'installing' }
  } catch (e) {
    console.error('[更新] 更新失败：', e)
    if (manual) {
      await showMessage({
        type: 'error',
        title: '更新失败',
        message: '下载或安装更新时出错',
        detail: String(e.message || e)
      })
    }
    return { status: 'error', message: String(e.message || e) }
  } finally {
    checking = false
  }
}

module.exports = { initUpdater, checkForUpdate, isNewer, verifyManifest }
