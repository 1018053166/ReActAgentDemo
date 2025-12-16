#!/bin/bash

###############################################################################
# Electron 客户端编译启动脚本
# 功能：编译 React UI → 启动 Electron 客户端（自动启动内嵌 Node.js 后端）
# 用法：
#   ./start-frontend.sh              # 开发模式启动（支持热重载）
#   ./start-frontend.sh --build      # 编译后启动（生产模式）
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
FRONTEND_DIR="$PROJECT_ROOT/electron-react-mcp"
UI_DIR="$FRONTEND_DIR/react-ui"

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
BUILD_MODE=false
if [[ "$1" == "--build" ]]; then
    BUILD_MODE=true
    log_info "生产模式: 先编译 React UI 再启动"
fi

# 打印横幅
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         Electron 客户端启动脚本                               ║"
echo "║         React UI + BrowserView + Node.js Backend              ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

###############################################################################
# 步骤 1: 检查并安装依赖
###############################################################################
log_info "步骤 1/3: 检查客户端依赖..."
cd "$FRONTEND_DIR"

if [ ! -d "node_modules" ]; then
    log_warning "node_modules 不存在，执行 npm install..."
    npm install
    log_success "依赖安装完成"
else
    log_info "node_modules 已存在，跳过依赖安装"
fi

# 检查 react-ui 依赖
log_info "检查 React UI 依赖..."
cd "$UI_DIR"

if [ ! -d "node_modules" ]; then
    log_warning "react-ui/node_modules 不存在，执行 npm install..."
    npm install
    log_success "React UI 依赖安装完成"
else
    log_info "react-ui/node_modules 已存在"
fi

###############################################################################
# 步骤 2: 编译 React UI（生产模式）
###############################################################################
if [ "$BUILD_MODE" = true ]; then
    log_info "步骤 2/3: 编译 React UI（生产模式）..."
    cd "$UI_DIR"
    
    log_info "执行 npm run build..."
    npm run build
    
    if [ -d "build" ]; then
        log_success "React UI 编译完成: $UI_DIR/build"
    else
        log_error "React UI 编译失败，未找到 build 目录"
        exit 1
    fi
else
    log_info "步骤 2/3: 跳过 React UI 编译（开发模式，使用 public/index.html）"
fi

###############################################################################
# 步骤 3: 启动 Electron 客户端
###############################################################################
log_info "步骤 3/3: 启动 Electron 客户端..."
cd "$FRONTEND_DIR"

# 检查端口 8080（Node.js 后端端口）
log_info "检查端口 8080..."
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
    log_warning "端口 8080 已被占用，客户端将连接到现有 Node.js 后端服务"
else
    log_info "端口 8080 空闲，Electron 启动后将自动拉起内嵌 Node.js 后端"
fi

# 启动 Electron
log_info "启动 Electron..."
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                 客户端正在启动...                             ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

npm start

###############################################################################
# 说明：npm start 会启动 Electron，后续日志由 Electron 主进程输出
###############################################################################
