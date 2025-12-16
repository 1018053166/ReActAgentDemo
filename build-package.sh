#!/bin/bash

###############################################################################
# ReAct MCP 客户端打包脚本
# 功能：复制 Node.js 后端 → React UI 编译 → 客户端打包 → 生成安装包
# 用法：
#   ./build-package.sh              # 完整流程（复制后端 + 打包客户端）
###############################################################################

set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_ROOT/node-mcp-backend"
FRONTEND_DIR="$PROJECT_ROOT/electron-react-mcp"

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查参数
SKIP_BACKEND=false

# 打印横幅
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         ReAct MCP 客户端打包脚本                              ║"
echo "║         Electron + Node.js 一体化打包                        ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

###############################################################################
# 步骤 1/3: 复制 Node.js 后端到客户端目录
###############################################################################
log_info "步骤 1/3: 复制 Node.js 后端到客户端项目..."
TARGET_DIR="$FRONTEND_DIR/node-backend"

# 确保目标目录存在
mkdir -p "$TARGET_DIR"

# 复制 Node.js 后端文件
log_info "正在复制 node-mcp-backend 到 $TARGET_DIR"
cp -r "$BACKEND_DIR/"* "$TARGET_DIR/"

if [ -f "$TARGET_DIR/package.json" ]; then
    log_success "Node.js 后端已复制到: $TARGET_DIR"
else
    log_error "Node.js 后端复制失败"
    exit 1
fi

###############################################################################
# 步骤 2/3: 安装客户端依赖（如果需要）
###############################################################################
log_info "步骤 2/3: 检查并安装客户端依赖..."
cd "$FRONTEND_DIR"

if [ ! -d "node_modules" ]; then
    log_info "node_modules 不存在，执行 npm install..."
    npm install
    log_success "依赖安装完成"
else
    log_info "node_modules 已存在，跳过依赖安装"
fi

###############################################################################
# 步骤 3/3: 打包客户端生成安装包
###############################################################################
log_info "步骤 3/3: 打包 Electron 客户端..."
log_info "执行 electron-builder 打包..."

npm run dist

# 检查打包结果
if [ -d "dist" ]; then
    log_success "客户端打包完成！"
    echo ""
    log_info "📦 打包产物位置:"
    
    # 列出生成的安装包
    if [ "$(uname)" == "Darwin" ]; then
        # macOS
        if [ -f "dist/"*.dmg ]; then
            DMG_FILE=$(ls dist/*.dmg 2>/dev/null | head -1)
            DMG_SIZE=$(du -h "$DMG_FILE" | cut -f1)
            echo "  ✓ DMG 安装包: $DMG_FILE ($DMG_SIZE)"
        fi
    elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
        # Linux
        if [ -f "dist/"*.AppImage ]; then
            echo "  ✓ AppImage: $(ls dist/*.AppImage)"
        fi
        if [ -f "dist/"*.deb ]; then
            echo "  ✓ DEB 包: $(ls dist/*.deb)"
        fi
    elif [ "$(expr substr $(uname -s) 1 10)" == "MINGW32_NT" ] || [ "$(expr substr $(uname -s) 1 10)" == "MINGW64_NT" ]; then
        # Windows
        if [ -f "dist/"*.exe ]; then
            echo "  ✓ EXE 安装包: $(ls dist/*.exe)"
        fi
    fi
    
    echo ""
    log_success "🎉 打包完成！可以分发给用户安装了。"
else
    log_error "打包失败，未找到 dist 目录"
    exit 1
fi

###############################################################################
# 打印总结
###############################################################################
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                     打包流程完成                              ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
log_info "下一步操作："
echo "  1. 测试安装包: 双击 DMG/EXE 文件安装"
echo "  2. 验证功能: 启动应用并测试 ReAct 代理"
echo "  3. 分发安装包: 将安装包分发给团队成员"
echo ""
