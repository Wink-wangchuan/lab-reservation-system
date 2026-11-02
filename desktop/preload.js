// 预加载脚本：向协议页/错误页暴露最小化 IPC 能力
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('labwise', {
  accept: () => ipcRenderer.invoke('eula:accept'),
  decline: () => ipcRenderer.invoke('eula:decline'),
  retry: () => ipcRenderer.invoke('app:retry'),
  checkUpdate: () => ipcRenderer.invoke('app:check-update')
})
