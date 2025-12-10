const fs = require('fs-extra');
const path = require('path');
const { execSync } = require('child_process');

// 构建 Electron 应用的脚本
async function buildElectronApp() {
  console.log('🚀 开始构建 Electron 应用...');
  
  try {
    // 1. 构建 React 前端
    console.log('🔨 构建 React 前端...');
    execSync('cd react-ui && npm run build', { stdio: 'inherit' });
    
    // 2. 复制 Spring Boot JAR 文件到 Electron 项目（如果不存在）
    const jarSource = '../react-mcp-demo/target/react-mcp-demo-0.0.1-SNAPSHOT.jar';
    const jarDest = 'spring-boot-server/react-mcp-demo-0.0.1-SNAPSHOT.jar';
    
    if (fs.existsSync(jarSource)) {
      console.log('📦 复制 Spring Boot JAR 文件...');
      await fs.copy(jarSource, jarDest);
    } else {
      // 如果源文件不存在，检查目标文件是否存在
      if (!fs.existsSync(jarDest)) {
        throw new Error(`Spring Boot JAR 文件不存在: ${jarSource}。请先构建 Spring Boot 项目。`);
      }
      console.log('✅ Spring Boot JAR 文件已存在');
    }
    
    // 3. 使用 electron-builder 打包应用
    console.log('📦 使用 electron-builder 打包应用...');
    execSync('npx electron-builder', { stdio: 'inherit' });
    
    // 4. 显示构建结果
    console.log('\n🎉 Electron 应用构建完成！');
    console.log('📁 输出目录: dist/');
    
    // 列出生成的文件
    const distPath = path.join(__dirname, '..', 'dist');
    if (fs.existsSync(distPath)) {
      console.log('\n📋 生成的文件:');
      const files = fs.readdirSync(distPath);
      files.forEach(file => {
        const stats = fs.statSync(path.join(distPath, file));
        if (stats.isDirectory()) {
          console.log(`   📁 ${file}/`);
        } else {
          console.log(`   📄 ${file} (${(stats.size / 1024 / 1024).toFixed(2)} MB)`);
        }
      });
    }
    
    console.log('\n💡 提示:');
    console.log('   - macOS 应用: dist/mac/ReAct MCP 客户端.app');
    console.log('   - Windows 应用: dist/win-unpacked/');
    console.log('   - Linux 应用: dist/linux-unpacked/');
    
  } catch (error) {
    console.error('\n❌ 构建失败:', error.message);
    if (error.stdout) {
      console.error('STDOUT:', error.stdout.toString());
    }
    if (error.stderr) {
      console.error('STDERR:', error.stderr.toString());
    }
    process.exit(1);
  }
}

// 执行构建
buildElectronApp();