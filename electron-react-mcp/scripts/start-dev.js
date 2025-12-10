const { spawn } = require('child_process');
const path = require('path');
const net = require('net');

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

// 启动开发环境的脚本
async function startDevEnvironment() {
  console.log('🚀 启动 ReAct MCP 开发环境...');
  
  // 检查 8080 端口是否可用
  const portAvailable = await isPortAvailable(8080);
  if (!portAvailable) {
    console.log('⚠️  端口 8080 已被占用，跳过 Spring Boot 启动');
    console.log('🖥️ 直接启动 Electron 应用...');
    
    const electronProcess = spawn('npm', ['start'], {
      cwd: path.join(__dirname, '..'),
      stdio: 'inherit'
    });
    
    electronProcess.on('error', (error) => {
      console.error('❌ Electron 启动失败:', error.message);
    });
    
    electronProcess.on('exit', (code) => {
      console.log(`🖥️ Electron 应用已退出 (代码: ${code})`);
    });
    
    // 优雅关闭
    process.on('SIGINT', () => {
      console.log('\n🛑 正在关闭 Electron 应用...');
      electronProcess.kill('SIGTERM');
      process.exit(0);
    });
    
    return;
  }
  
  // 启动 Spring Boot 服务
  console.log('🔧 启动 Spring Boot 服务...');
  const springBootProcess = spawn('mvn', ['spring-boot:run'], {
    cwd: path.join(__dirname, '..', '..', 'react-mcp-demo'),
    stdio: 'inherit'
  });
  
  // 等待 Spring Boot 服务启动
  console.log('⏳ 等待 Spring Boot 服务启动 (约10-15秒)...');
  
  // 启动 Electron 应用
  setTimeout(() => {
    console.log('🖥️ 启动 Electron 应用...');
    const electronProcess = spawn('npm', ['start'], {
      cwd: path.join(__dirname, '..'),
      stdio: 'inherit'
    });
    
    electronProcess.on('error', (error) => {
      console.error('❌ Electron 启动失败:', error.message);
    });
    
    electronProcess.on('exit', (code) => {
      console.log(`🖥️ Electron 应用已退出 (代码: ${code})`);
    });
  }, 15000); // 等待 15 秒让 Spring Boot 启动
  
  springBootProcess.on('error', (error) => {
    console.error('❌ Spring Boot 启动失败:', error.message);
  });
  
  springBootProcess.on('exit', (code) => {
    console.log(`🔧 Spring Boot 服务已退出 (代码: ${code})`);
  });
  
  // 优雅关闭
  process.on('SIGINT', () => {
    console.log('\n🛑 正在关闭所有服务...');
    springBootProcess.kill('SIGTERM');
    process.exit(0);
  });
}

// 执行启动
startDevEnvironment();