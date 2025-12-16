#!/usr/bin/env node

/**
 * Electron 客户端打包脚本
 * 功能：
 * 1. 拷贝 Node.js 后端到打包目录
 * 2. 执行 electron-builder 打包
 */

const fs = require('fs-extra');
const path = require('path');
const { execSync } = require('child_process');

const ROOT_DIR = path.join(__dirname, '..');
const BACKEND_SRC = path.join(ROOT_DIR, '../node-mcp-backend');
const BACKEND_DEST = path.join(ROOT_DIR, 'node-backend');

async function main() {
  console.log('╔════════════════════════════════════════════════════════╗');
  console.log('║  ReAct MCP 客户端打包脚本                               ║');
  console.log('╚════════════════════════════════════════════════════════╝\n');

  try {
    // 步骤 1: 清理旧的打包目录
    console.log('📦 [1/4] 清理旧的打包目录...');
    if (fs.existsSync(BACKEND_DEST)) {
      fs.removeSync(BACKEND_DEST);
      console.log('✅ 清理完成\n');
    } else {
      console.log('✅ 无需清理\n');
    }

    // 步骤 2: 拷贝 Node.js 后端
    console.log('📦 [2/4] 拷贝 Node.js 后端...');
    fs.copySync(BACKEND_SRC, BACKEND_DEST, {
      filter: (src) => {
        // 过滤掉不需要的文件
        const relativePath = path.relative(BACKEND_SRC, src);
        if (relativePath.includes('node_modules')) return false;
        if (relativePath.includes('.git')) return false;
        if (relativePath.includes('dist')) return false;
        if (relativePath.endsWith('.log')) return false;
        return true;
      }
    });
    console.log(`✅ 后端已拷贝: ${BACKEND_SRC} -> ${BACKEND_DEST}\n`);

    // 步骤 3: 安装后端依赖
    console.log('📦 [3/4] 安装后端依赖...');
    execSync('npm install --production', {
      cwd: BACKEND_DEST,
      stdio: 'inherit'
    });
    console.log('✅ 后端依赖安装完成\n');

    // 步骤 4: 执行 electron-builder 打包
    console.log('📦 [4/4] 开始打包 Electron 客户端...');
    console.log('提示: 使用 npm run dist:mac 或 npm run dist:win 指定平台\n');
    
    console.log('╔════════════════════════════════════════════════════════╗');
    console.log('║  打包准备完成！                                         ║');
    console.log('╠════════════════════════════════════════════════════════╣');
    console.log('║  下一步执行:                                            ║');
    console.log('║  - npm run dist:mac   (Mac 安装包)                     ║');
    console.log('║  - npm run dist:win   (Windows 安装包)                 ║');
    console.log('║  - npm run dist:all   (所有平台)                       ║');
    console.log('╚════════════════════════════════════════════════════════╝');

  } catch (error) {
    console.error('❌ 打包失败:', error.message);
    process.exit(1);
  }
}

main();
