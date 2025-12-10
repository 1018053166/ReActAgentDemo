// 在main.js中添加的代码
// 在createWindow函数中，修改窗口创建部分

// 1. 修改窗口创建，添加show: false
const mainWindow = new BrowserWindow({
  show: false, // 初始不显示窗口，等待内容加载完成后再显示
  width: 1400,
  height: 900,
  minWidth: 1000,
  minHeight: 700,
  webPreferences: {
    preload: path.join(__dirname, 'preload.js'),
    nodeIntegration: false,
    contextIsolation: true,
  },
});

// 2. 在加载文件后添加事件监听
mainWindow.loadFile(path.join(__dirname, 'react-ui/build/index.html')).catch(err => console.error("Failed to load file:", err));

// 等待内容加载完成后显示窗口
mainWindow.once('ready-to-show', () => {
  console.log('[MAIN] 窗口准备显示');
  mainWindow.show();
});

// 监听加载完成事件
mainWindow.webContents.on('did-finish-load', () => {
  console.log('[MAIN] 内容加载完成');
});

// 监听加载失败事件
mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription) => {
  console.error('[MAIN] 内容加载失败:', errorCode, errorDescription);
});
