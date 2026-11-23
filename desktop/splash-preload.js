// 启动画面窗口的预加载脚本：只暴露「画面播放完毕」一个能力
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('labwiseSplash', {
  done: () => ipcRenderer.send('splash:done')
})
