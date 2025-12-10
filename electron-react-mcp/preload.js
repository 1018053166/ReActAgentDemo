const { contextBridge, ipcRenderer } = require('electron');

// 暴露给渲染进程的API
contextBridge.exposeInMainWorld('electronAPI', {
  // 调用主进程的方法
  invoke: (channel, ...args) => ipcRenderer.invoke(channel, ...args),
  
  // 监听主进程发送的消息
  on: (channel, func) => {
    const validChannels = ['spring-boot-status'];
    if (validChannels.includes(channel)) {
      ipcRenderer.on(channel, (event, ...args) => func(...args));
    }
  },
  
  // 移除监听器
  removeListener: (channel, func) => {
    ipcRenderer.removeListener(channel, func);
  }
});
