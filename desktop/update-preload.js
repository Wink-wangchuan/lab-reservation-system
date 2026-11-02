// 更新进度窗预加载：只暴露版本号与进度回调
const { contextBridge, ipcRenderer } = require('electron')

const version = new URLSearchParams(location.search).get('version') || ''

contextBridge.exposeInMainWorld('updateWin', {
  version,
  onProgress: (callback) => {
    ipcRenderer.on('update:progress', (_event, data) => callback(data))
  }
})
