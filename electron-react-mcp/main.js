const { app, BrowserWindow, ipcMain, BrowserView } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const isDev = !app.isPackaged;
const net = require('net');
const http = require('http');
const url = require('url');
const { playwrightManager } = require('./playwright-manager');

// 在 app.ready 之前禁用硬件加速以减少 EGL 错误
app.disableHardwareAcceleration();

// 后端服务进程（支持 Spring Boot 或 Node.js）
let backendProcess = null;
// 后端服务端口
const BACKEND_PORT = 8080;
// 服务启动超时时间（毫秒）
const SERVICE_START_TIMEOUT = 30000;
// 后端类型：'springboot' 或 'nodejs'
const BACKEND_TYPE = process.env.BACKEND_TYPE || 'nodejs'; // 默认使用 Node.js
// BrowserView 实例
let browserView = null;
// 远程控制 HTTP 服务器
let controlServer = null;
// 远程控制端口
const CONTROL_PORT = 9222;
// Playwright 远程调试端口（用于 CDP 连接）
const PLAYWRIGHT_DEBUG_PORT = 9223;

// 主窗口引用
let mainWindow = null;

// 创建窗口函数
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1000,
    minHeight: 700,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
      // 允许从 file:// 协议连接 localhost
      webSecurity: false,
    },
  });

  // 加载本地 UI 页面（客户端项目中的 HTML 文件）
  const localHtmlPath = isDev 
    ? path.join(__dirname, 'react-ui/public/index.html')
    : path.join(process.resourcesPath, 'react-ui/build/index.html');
  console.log('[MAIN] 加载本地 UI 页面:', localHtmlPath);
  console.log('[MAIN] isDev:', isDev, 'resourcesPath:', process.resourcesPath, '__dirname:', __dirname);
  mainWindow.loadFile(localHtmlPath).catch(err => {
    console.error('[MAIN] 加载本地页面失败:', err);
  });

  // 临时开启主窗口开发者工具调试
  mainWindow.webContents.openDevTools({ mode: 'detach' });

  // 创建 BrowserView 用于嵌入浏览器
  createBrowserView(mainWindow);
}

// 创建 BrowserView
function createBrowserView(mainWindow) {
  browserView = new BrowserView({
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      // 启用 WebView 标签支持
      webviewTag: true,
      // 启用 DevTools
      devTools: true,
      // 启用 WebGL
      webSecurity: true,
      // 启用实验性功能
      experimentalFeatures: true,
      // 启用滚动动画
      scrollBounce: true
    }
  });
  
  // 先设置 BrowserView
  mainWindow.setBrowserView(browserView);
  
  // 设置 BrowserView 位置和大小（严格三七分布局：左侧30%控制面板，右侧70%浏览器）
  const resizeBrowserView = () => {
    const contentSize = mainWindow.getContentSize();
    const totalWidth = contentSize[0];
    const totalHeight = contentSize[1];
    
    // 严格三七分：左侧30%给控制面板，右侧70%给BrowserView
    const leftPanelWidth = Math.floor(totalWidth * 0.3);
    const browserViewX = leftPanelWidth; // BrowserView从左侧30%位置开始
    const browserViewWidth = totalWidth - leftPanelWidth; // 剩余70%宽度
    
    console.log(`[MAIN] 布局调整: 总宽度=${totalWidth}, 左侧面板=${leftPanelWidth}, BrowserView起始X=${browserViewX}, BrowserView宽度=${browserViewWidth}`);
    
    browserView.setBounds({
      x: browserViewX,
      y: 0,
      width: browserViewWidth,
      height: totalHeight
    });
    
    browserView.setAutoResize({ width: true, height: true });
  };
  
  // 初始设置
  resizeBrowserView();
  
  // 监听窗口大小变化
  mainWindow.on('resize', resizeBrowserView);
  
  // 加载默认页面
  browserView.webContents.loadURL('https://example.com');
  
  // 启用开发者工具
  browserView.webContents.openDevTools({ mode: 'detach' });
  
  // 处理新窗口事件
  browserView.webContents.setWindowOpenHandler(({ url }) => {
    // 在默认浏览器中打开链接
    require('electron').shell.openExternal(url);
    return { action: 'deny' };
  });
  
  // 监听页面加载完成事件
  browserView.webContents.on('did-finish-load', () => {
    console.log('[MAIN] BrowserView 页面加载完成');
  });
  
  // 监听页面加载失败事件
  browserView.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    console.error(`[MAIN] BrowserView 页面加载失败: ${errorCode} ${errorDescription} ${validatedURL}`);
  });
  
  // 关键：确保 BrowserView 在正确的层级
  browserView.setBackgroundColor('#FFFFFF');
}

// 启动远程控制 HTTP 服务器 (使用 Playwright 统一控制)
function startControlServer() {
  controlServer = http.createServer(async (req, res) => {
    const parsedUrl = url.parse(req.url, true);
    const pathname = parsedUrl.pathname;
    
    // 设置 CORS 头
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    
    // 处理预检请求
    if (req.method === 'OPTIONS') {
      res.writeHead(200);
      res.end();
      return;
    }

    // 辅助函数：发送成功响应
    const sendSuccess = (data) => {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, ...data }));
    };

    // 辅助函数：发送错误响应
    const sendError = (message, statusCode = 500) => {
      res.writeHead(statusCode, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, message }));
    };

    try {
      // ==================== 导航 ====================
      if (pathname === '/browser/navigate' && req.method === 'GET') {
        const targetUrl = parsedUrl.query.url;
        if (!targetUrl) {
          return sendError('Missing url parameter', 400);
        }
        
        const decodedUrl = decodeURIComponent(targetUrl);
        
        // 使用 Playwright 导航（保持 page 引用一致）
        try {
          const result = await playwrightManager.navigate(decodedUrl);
          return sendSuccess({ 
            message: 'Navigation completed',
            url: result.url,
            title: result.title
          });
        } catch (error) {
          // 如果 Playwright 导航失败，回退到 BrowserView 原生 API
          console.log('[MAIN] Playwright 导航失败，使用原生 API:', error.message);
          if (browserView) {
            browserView.webContents.loadURL(decodedUrl);
            // 等待页面加载后刷新 Playwright 连接
            setTimeout(async () => {
              try {
                await playwrightManager.refreshPage();
              } catch (e) {
                console.log('[MAIN] Playwright 刷新页面引用:', e.message);
              }
            }, 2000);
          }
          return sendSuccess({ 
            message: 'Navigation started (fallback)',
            url: decodedUrl
          });
        }
      }

      // ==================== 点击 ====================
      if (pathname === '/browser/click' && req.method === 'GET') {
        const selector = parsedUrl.query.selector;
        if (!selector) {
          return sendError('Missing selector parameter', 400);
        }
        
        const result = await playwrightManager.click(selector);
        return sendSuccess({ message: 'Click executed successfully', result });
      }

      // ==================== 填充输入框 ====================
      if (pathname === '/browser/fill' && req.method === 'GET') {
        const selector = parsedUrl.query.selector;
        const text = parsedUrl.query.text;
        if (!selector || text === undefined) {
          return sendError('Missing selector or text parameter', 400);
        }
        
        const result = await playwrightManager.fill(selector, decodeURIComponent(text));
        return sendSuccess({ message: 'Fill executed successfully', result });
      }

      // ==================== 按键 ====================
      if (pathname === '/browser/press' && req.method === 'GET') {
        const key = parsedUrl.query.key;
        if (!key) {
          return sendError('Missing key parameter', 400);
        }
        
        const result = await playwrightManager.press(key);
        return sendSuccess({ message: `Pressed key: ${key}`, result });
      }

      // ==================== 获取页面信息 ====================
      if (pathname === '/browser/getPageInfo' && req.method === 'GET') {
        const result = await playwrightManager.getPageInfo();
        return sendSuccess({ message: 'Page info retrieved successfully', result });
      }

      // ==================== 获取页面 URL ====================
      if (pathname === '/browser/getPageUrl' && req.method === 'GET') {
        const url = await playwrightManager.getPageUrl();
        return sendSuccess({ url });
      }

      // ==================== 获取页面标题 ====================
      if (pathname === '/browser/getPageTitle' && req.method === 'GET') {
        const title = await playwrightManager.getPageTitle();
        return sendSuccess({ title });
      }

      // ==================== 获取可见文本 ====================
      if (pathname === '/browser/getVisibleText' && req.method === 'GET') {
        const selector = parsedUrl.query.selector || 'body';
        const result = await playwrightManager.getVisibleText(selector);
        return sendSuccess({ message: 'Visible text retrieved successfully', result });
      }

      // ==================== 获取 HTML ====================
      if (pathname === '/browser/getVisibleHtml' && req.method === 'GET') {
        const selector = parsedUrl.query.selector || 'html';
        const cleanHtml = parsedUrl.query.cleanHtml === 'true';
        const result = await playwrightManager.getVisibleHtml(selector, cleanHtml);
        return sendSuccess({ message: 'HTML content retrieved successfully', result });
      }

      // ==================== 执行 JavaScript ====================
      if (pathname === '/browser/executeJs' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => { body += chunk.toString(); });
        
        return new Promise((resolve) => {
          req.on('end', async () => {
            try {
              const { script } = JSON.parse(body);
              if (!script) {
                sendError('Script is required', 400);
                return resolve();
              }
              
              const result = await playwrightManager.executeJs(script);
              sendSuccess({ message: 'JavaScript executed successfully', result });
              resolve();
            } catch (error) {
              sendError(error.message);
              resolve();
            }
          });
        });
      }

      // ==================== 截图 ====================
      if (pathname === '/browser/screenshot' && req.method === 'GET') {
        const fullPage = parsedUrl.query.fullPage === 'true';
        const selector = parsedUrl.query.selector || null;
        
        const buffer = await playwrightManager.screenshot({ fullPage, selector });
        const base64 = buffer.toString('base64');
        return sendSuccess({ message: 'Screenshot captured successfully', result: base64 });
      }

      // ==================== 悬停 ====================
      if (pathname === '/browser/hover' && req.method === 'GET') {
        const selector = parsedUrl.query.selector;
        if (!selector) {
          return sendError('Missing selector parameter', 400);
        }
        
        const result = await playwrightManager.hover(selector);
        return sendSuccess({ message: 'Hover executed successfully', result });
      }

      // ==================== 选择下拉框 ====================
      if (pathname === '/browser/select' && req.method === 'GET') {
        const selector = parsedUrl.query.selector;
        const value = parsedUrl.query.value;
        if (!selector || !value) {
          return sendError('Missing selector or value parameter', 400);
        }
        
        const result = await playwrightManager.select(selector, value);
        return sendSuccess({ message: 'Select executed successfully', result });
      }

      // ==================== 等待元素 ====================
      if (pathname === '/browser/waitForSelector' && req.method === 'GET') {
        const selector = parsedUrl.query.selector;
        if (!selector) {
          return sendError('Missing selector parameter', 400);
        }
        
        const result = await playwrightManager.waitForSelector(selector);
        return sendSuccess({ message: 'Element found', result });
      }

      // ==================== 调试：获取输入框信息 ====================
      if (pathname === '/browser/debug/inputs' && req.method === 'GET') {
        const inputs = await playwrightManager.getInputs();
        return sendSuccess({ inputs });
      }

      // ==================== 分析页面 ====================
      if (pathname === '/browser/analyzePage' && req.method === 'GET') {
        const result = await playwrightManager.analyzePage();
        return sendSuccess({ message: 'Page analyzed successfully', result });
      }

      // ==================== 控制台日志（使用 Playwright evaluate）====================
      if (pathname === '/browser/consoleLogs' && req.method === 'GET') {
        // 注：Playwright 原生支持 console 事件监听，这里用简化实现
        const result = await playwrightManager.executeJs(`
          (function() {
            return window.__consoleLogs || [];
          })()
        `);
        return sendSuccess({ message: 'Console logs retrieved', result: result || [] });
      }

      // ==================== Playwright 连接状态 ====================
      if (pathname === '/browser/status' && req.method === 'GET') {
        return sendSuccess({ 
          connected: playwrightManager.isConnected(),
          message: playwrightManager.isConnected() ? 'Playwright connected' : 'Playwright not connected'
        });
      }

      // ==================== 重新连接 Playwright ====================
      if (pathname === '/browser/reconnect' && req.method === 'GET') {
        await playwrightManager.connect(PLAYWRIGHT_DEBUG_PORT);
        return sendSuccess({ message: 'Playwright reconnected' });
      }

      // 默认路由
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, message: 'Not found' }));

    } catch (error) {
      console.error('[MAIN] 控制服务器错误:', error.message);
      sendError(error.message);
    }
  });
  
  // 监听端口
  controlServer.listen(CONTROL_PORT, '0.0.0.0', () => {
    console.log(`[MAIN] 远程控制服务器启动于 http://0.0.0.0:${CONTROL_PORT}`);
    
    // 延迟连接 Playwright（等待 BrowserView 初始化完成）
    setTimeout(async () => {
      try {
        await playwrightManager.connect(PLAYWRIGHT_DEBUG_PORT);
        console.log('[MAIN] Playwright 已连接到 BrowserView');
      } catch (error) {
        console.log('[MAIN] Playwright 连接延迟，将在首次请求时连接:', error.message);
      }
    }, 3000);
  });
  
  controlServer.on('error', (error) => {
    console.error(`[MAIN] 远程控制服务器启动失败: ${error.message}`);
  });
}

// 停止远程控制 HTTP 服务器
function stopControlServer() {
  if (controlServer) {
    controlServer.close(() => {
      console.log('[MAIN] 远程控制服务器已停止');
    });
    controlServer = null;
  }
  
  // 断开 Playwright 连接
  playwrightManager.disconnect().catch(err => {
    console.log('[MAIN] Playwright 断开连接:', err.message);
  });
}

// 检查端口是否可用
function isPortAvailable(port) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.listen(port, () => {
      server.close();
      resolve(true);
    });
    server.on('error', () => {
      resolve(false);
    });
  });
}

// 启动后端服务（支持 Spring Boot 和 Node.js）
async function startBackendService() {
  return new Promise(async (resolve, reject) => {
    console.log(`[MAIN] 检查后端服务端口... (类型: ${BACKEND_TYPE})`);
    
    // 检查端口是否已被占用
    const portAvailable = await isPortAvailable(BACKEND_PORT);
    if (!portAvailable) {
      console.log('[MAIN] 后端服务已在运行');
      resolve({ port: BACKEND_PORT, started: false });
      return;
    }

    if (BACKEND_TYPE === 'nodejs') {
      await startNodeJsBackend(resolve, reject);
    } else if (BACKEND_TYPE === 'springboot') {
      await startSpringBootBackend(resolve, reject);
    } else {
      reject(new Error(`不支持的后端类型: ${BACKEND_TYPE}`));
    }

    // 设置超时
    setTimeout(() => {
      reject(new Error('后端服务启动超时'));
    }, SERVICE_START_TIMEOUT);
  });
}

// 启动 Node.js 后端（直接在主进程中运行）
let nodeBackendServer = null;

async function startNodeJsBackend(resolve, reject) {
  console.log('[MAIN] 启动 Node.js 后端服务...');
  
  try {
    const fs = require('fs');
    
    // 确定 Node.js 后端路径
    const serverPath = isDev 
      ? path.join(__dirname, 'node-backend/src/server.js')
      : path.join(process.resourcesPath, 'app.asar.unpacked/node-backend/src/server.js');
    
    const backendDir = path.dirname(path.dirname(serverPath));
    const envPath = path.join(backendDir, '.env');
    const envExamplePath = path.join(backendDir, '.env.example');
    
    console.log('[MAIN] Node.js 后端路径:', serverPath);
    console.log('[MAIN] 后端目录:', backendDir);
    console.log('[MAIN] 文件是否存在:', fs.existsSync(serverPath));
    
    // 检查 .env 文件
    if (!fs.existsSync(envPath) && fs.existsSync(envExamplePath)) {
      console.log('[MAIN] .env 文件不存在，从 .env.example 创建...');
      fs.copyFileSync(envExamplePath, envPath);
    }
    
    // 设置环境变量
    process.env.PORT = BACKEND_PORT.toString();
    process.env.NODE_ENV = isDev ? 'development' : 'production';
    
    // 直接 require 后端模块（CommonJS）
    console.log('[MAIN] 加载后端模块:', serverPath);
    const serverModule = require(serverPath);
    
    // 调用 startServer 启动服务
    const result = await serverModule.startServer(BACKEND_PORT);
    nodeBackendServer = result.server;
    
    console.log('[MAIN] Node.js 后端服务启动成功');
    resolve({ port: BACKEND_PORT, started: true });
    
  } catch (error) {
    console.error('[MAIN] 启动 Node.js 后端服务失败:', error);
    reject(error);
  }
}

// 启动 Spring Boot 后端
async function startSpringBootBackend(resolve, reject) {
  console.log('[MAIN] 启动 Spring Boot 后端服务...');
  
  // 确定 JAR 文件路径
  const jarPath = isDev 
    ? path.join(__dirname, '../react-mcp-demo/target/react-mcp-demo-0.0.1-SNAPSHOT.jar')
    : path.join(process.resourcesPath, 'app.asar.unpacked/spring-boot-server/react-mcp-demo-0.0.1-SNAPSHOT.jar');
  
  console.log('[MAIN] JAR 文件路径:', jarPath);
  console.log('[MAIN] JAR 文件是否存在:', require('fs').existsSync(jarPath));

  // 启动 Spring Boot 应用
  backendProcess = spawn('java', ['-jar', jarPath], {
    cwd: path.dirname(jarPath),
  });

  backendProcess.stdout.on('data', (data) => {
    const output = data.toString();
    console.log(`[SPRING BOOT] ${output}`);
    
    // 检查服务是否启动完成
    if (output.includes('Started ReactMcpApplication')) {
      console.log('[MAIN] Spring Boot 服务启动成功');
      resolve({ port: BACKEND_PORT, started: true });
    }
  });

  backendProcess.stderr.on('data', (data) => {
    console.error(`[SPRING BOOT ERROR] ${data}`);
  });

  backendProcess.on('error', (error) => {
    console.error('[MAIN] 启动 Spring Boot 服务失败:', error);
    reject(error);
  });
}

// 停止后端服务
function stopBackendService() {
  if (BACKEND_TYPE === 'nodejs' && nodeBackendServer) {
    console.log('[MAIN] 停止 Node.js 后端服务...');
    nodeBackendServer.close();
    nodeBackendServer = null;
  } else if (backendProcess) {
    console.log(`[MAIN] 停止${BACKEND_TYPE === 'nodejs' ? 'Node.js' : 'Spring Boot'}后端服务...`);
    backendProcess.kill('SIGTERM');
    backendProcess = null;
  }
}

// 应用准备就绪
app.whenReady().then(async () => {
  try {
    // 启动远程控制服务器
    startControlServer();
    
    // 启动后端服务
    const serviceInfo = await startBackendService();
    
    // 创建主窗口
    createWindow();
    
    // 等待Spring Boot服务完全启动后再通知渲染进程
    setTimeout(async () => {
      // 检查后端服务是否真的启动完成
      let retries = 0;
      const maxRetries = 30; // 最多重试30次，每次2秒，总共60秒
      
      while (retries < maxRetries) {
        try {
          const response = await fetch(`http://localhost:${serviceInfo.port}/health`);
          if (response.ok) {
            console.log(`[MAIN] ${BACKEND_TYPE === 'nodejs' ? 'Node.js' : 'Spring Boot'}后端服务已完全启动，通知渲染进程`);
            
            // 通知渲染进程服务状态
            BrowserWindow.getAllWindows().forEach(window => {
              window.webContents.send('service-status', {
                status: 'ready',
                port: serviceInfo.port,
                message: serviceInfo.started ? '服务启动成功' : '服务已在运行'
              });
            });
            break;
          }
        } catch (error) {
          console.log(`[MAIN] 等待后端服务启动中... (${retries + 1}/${maxRetries})`);
        }
        
        retries++;
        await new Promise(resolve => setTimeout(resolve, 2000)); // 等待2秒
      }
      
      if (retries >= maxRetries) {
        console.error('[MAIN] 后端服务启动超时，通知渲染进程启动失败');
        
        // 通知渲染进程启动失败
        BrowserWindow.getAllWindows().forEach(window => {
          window.webContents.send('service-status', {
            status: 'error',
            message: '服务启动超时，请检查后端服务日志'
          });
        });
      }
    }, 5000); // 延迟5秒开始检查，给后端服务充分的启动时间
  } catch (error) {
    console.error('[MAIN] 启动失败:', error);
    
    // 通知渲染进程启动失败
    BrowserWindow.getAllWindows().forEach(window => {
      window.webContents.send('service-status', {
        status: 'error',
        message: `服务启动失败: ${error.message}`
      });
    });
  }

  // 应用激活时创建窗口
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

// 应用关闭时停止服务
app.on('before-quit', () => {
  stopBackendService();
  stopControlServer();
});

// 所有窗口关闭时退出应用（非 macOS）
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

// IPC 通信处理
ipcMain.handle('get-service-info', async () => {
  return {
    port: BACKEND_PORT,
    url: `http://localhost:${BACKEND_PORT}`,
    type: BACKEND_TYPE
  };
});

ipcMain.handle('check-service-status', async () => {
  try {
    const available = await isPortAvailable(BACKEND_PORT);
    return {
      running: !available, // 端口被占用表示服务正在运行
      port: BACKEND_PORT,
      type: BACKEND_TYPE
    };
  } catch (error) {
    return {
      running: false,
      error: error.message
    };
  }
});

// 处理 spring-boot-status IPC 请求（保持兼容性）
ipcMain.handle('spring-boot-status', async () => {
  try {
    // Node.js 后端直接检查服务实例（主进程内运行）
    if (BACKEND_TYPE === 'nodejs') {
      return {
        running: nodeBackendServer !== null,
        port: BACKEND_PORT,
        type: BACKEND_TYPE
      };
    }
    
    // Spring Boot 用端口检测
    const available = await isPortAvailable(BACKEND_PORT);
    return {
      running: !available,
      port: BACKEND_PORT,
      type: BACKEND_TYPE
    };
  } catch (error) {
    return {
      running: false,
      port: BACKEND_PORT,
      type: BACKEND_TYPE,
      error: error.message
    };
  }
});

// IPC 处理浏览器控制命令
ipcMain.handle('browser-navigate', async (event, url) => {
  if (browserView) {
    try {
      await browserView.webContents.loadURL(url);
      return { success: true, message: '页面加载成功' };
    } catch (error) {
      return { success: false, message: `页面加载失败: ${error.message}` };
    }
  }
  return { success: false, message: '浏览器视图未初始化' };
});

ipcMain.handle('browser-reload', async () => {
  if (browserView) {
    browserView.webContents.reload();
    return { success: true };
  }
  return { success: false, message: '浏览器视图未初始化' };
});

ipcMain.handle('browser-go-back', async () => {
  if (browserView) {
    browserView.webContents.goBack();
    return { success: true };
  }
  return { success: false, message: '浏览器视图未初始化' };
});

ipcMain.handle('browser-go-forward', async () => {
  if (browserView) {
    browserView.webContents.goForward();
    return { success: true };
  }
  return { success: false, message: '浏览器视图未初始化' };
});

ipcMain.handle('browser-execute-js', async (event, script) => {
  if (browserView) {
    try {
      const result = await browserView.webContents.executeJavaScript(script);
      return { success: true, result };
    } catch (error) {
      return { success: false, message: `脚本执行失败: ${error.message}` };
    }
  }
  return { success: false, message: '浏览器视图未初始化' };
});

ipcMain.handle('browser-get-title', async () => {
  if (browserView) {
    const title = browserView.webContents.getTitle();
    return { success: true, title };
  }
  return { success: false, message: '浏览器视图未初始化' };
});

ipcMain.handle('browser-get-url', async () => {
  if (browserView) {
    const url = browserView.webContents.getURL();
    return { success: true, url };
  }
  return { success: false, message: '浏览器视图未初始化' };
});