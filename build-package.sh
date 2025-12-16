#!/bin/bash

###############################################################################
# ReAct MCP 客户端打包脚本
# 功能：安装依赖 → 构建 React UI → 打包生成安装包
# 用法：
#   ./build-package.sh                    # 交互式选择平台
#   ./build-package.sh --mac              # 仅打包 macOS
#   ./build-package.sh --win              # 仅打包 Windows
#   ./build-package.sh --all              # 打包所有平台
#   ./build-package.sh --prepare          # 仅准备后端（不打包）
#   ./build-package.sh --auto             # 自动打包当前平台（非交互，适合 CI/CD）
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

# 解析命令行参数
PLATFORM=""
INTERACTIVE=true

while [[ $# -gt 0 ]]; do
    case $1 in
        --mac)
            PLATFORM="mac"
            INTERACTIVE=false
            shift
            ;;
        --win)
            PLATFORM="win"
            INTERACTIVE=false
            shift
            ;;
        --all)
            PLATFORM="all"
            INTERACTIVE=false
            shift
            ;;
        --prepare)
            PLATFORM="prepare"
            INTERACTIVE=false
            shift
            ;;
        --auto)
            PLATFORM="auto"
            INTERACTIVE=false
            shift
            ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "  --mac          仅打包 macOS 平台"
            echo "  --win          仅打包 Windows 平台"
            echo "  --all          打包所有平台"
            echo "  --prepare      仅准备后端（不打包）"
            echo "  --auto         自动打包当前平台（适合 CI/CD）"
            echo "  -h, --help     显示帮助信息"
            echo ""
            echo "无参数时使用交互式菜单选择平台"
            exit 0
            ;;
        *)
            log_error "未知参数: $1"
            echo "使用 --help 查看帮助信息"
            exit 1
            ;;
    esac
done

# 打印横幅
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         ReAct MCP 客户端打包脚本                              ║"
echo "║         Electron + Node.js 一体化打包                        ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

###############################################################################
# 步骤 1/4: 安装客户端依赖（如果需要）
###############################################################################
log_info "步骤 1/4: 检查并安装客户端依赖..."
cd "$FRONTEND_DIR"

if [ ! -d "node_modules" ]; then
    log_info "node_modules 不存在，执行 npm install..."
    npm install
    log_success "依赖安装完成"
else
    log_info "node_modules 已存在，跳过依赖安装"
fi

###############################################################################
# 步骤 2/4: 安装 Node.js 后端依赖
###############################################################################
log_info "步骤 2/4: 安装 Node.js 后端依赖..."
NODE_BACKEND_DIR="$FRONTEND_DIR/node-backend"

if [ -d "$NODE_BACKEND_DIR" ]; then
    cd "$NODE_BACKEND_DIR"
    if [ ! -d "node_modules" ]; then
        log_info "node-backend/node_modules 不存在，执行 npm install..."
        npm install --production
        log_success "Node.js 后端依赖安装完成"
    else
        log_info "node-backend/node_modules 已存在"
    fi
    cd "$FRONTEND_DIR"
else
    log_error "Node.js 后端目录不存在: $NODE_BACKEND_DIR"
    exit 1
fi

###############################################################################
# 步骤 3/4: 构建 React UI
###############################################################################
log_info "步骤 3/4: 构建 React UI..."
REACT_UI_DIR="$FRONTEND_DIR/react-ui"

if [ -d "$REACT_UI_DIR" ]; then
    cd "$REACT_UI_DIR"
    if [ ! -d "node_modules" ]; then
        log_info "React UI node_modules 不存在，安装依赖..."
        npm install
    fi
    log_info "构建 React UI..."
    npm run build
    if [ -d "build" ]; then
        log_success "React UI 构建完成"
    else
        log_error "React UI 构建失败"
        exit 1
    fi
    cd "$FRONTEND_DIR"
else
    log_warning "React UI 目录不存在，跳过构建"
fi

###############################################################################
# 选择打包平台（交互式或命令行参数）
###############################################################################
if [ "$INTERACTIVE" = true ]; then
    log_info "选择打包平台..."
    echo ""
    echo "请选择打包平台:"
    echo "  1) Mac (dmg)"
    echo "  2) Windows (exe)"
    echo "  3) 所有平台"
    echo "  4) 仅准备（不打包）"
    echo ""
    read -p "请输入选项 [1-4]: " choice
    
    case $choice in
        1)
            PLATFORM="mac"
            ;;
        2)
            PLATFORM="win"
            ;;
        3)
            PLATFORM="all"
            ;;
        4)
            PLATFORM="prepare"
            ;;
        *)
            log_error "无效选项"
            exit 1
            ;;
    esac
fi

# 如果选择仅准备，则退出
if [ "$PLATFORM" = "prepare" ]; then
    log_success "准备完成，跳过打包"
    echo ""
    log_info "下一步操作："
    echo "  1. 手动打包 Mac: cd electron-react-mcp && npm run dist:mac"
    echo "  2. 手动打包 Windows: cd electron-react-mcp && npm run dist:win"
    echo "  3. 手动打包所有: cd electron-react-mcp && npm run dist:all"
    exit 0
fi

###############################################################################
# 步骤 4/4: 打包客户端生成安装包
###############################################################################
log_info "步骤 4/4: 打包 Electron 客户端..."

case $PLATFORM in
    mac)
        log_info "打包 macOS 版本..."
        npm run dist:mac
        ;;
    win)
        log_info "打包 Windows 版本..."
        npm run dist:win
        ;;
    all)
        log_info "打包所有平台..."
        npm run dist:all
        ;;
    auto)
        log_info "自动打包当前平台..."
        npm run dist
        ;;
    *)
        log_error "未知平台: $PLATFORM"
        exit 1
        ;;
esac

###############################################################################
# 步骤 5: 复制后端 node_modules 到打包结果
###############################################################################
log_info "复制 node-backend/node_modules 到打包结果..."

# macOS
MAC_APP_PATH="dist/mac/ReAct MCP 客户端.app/Contents/Resources/app.asar.unpacked/node-backend"
if [ -d "$MAC_APP_PATH" ]; then
    if [ -d "node-backend/node_modules" ]; then
        log_info "复制 node_modules 到 macOS 应用..."
        cp -R node-backend/node_modules "$MAC_APP_PATH/"
        log_success "macOS 后端依赖复制完成"
    fi
fi

# Windows
WIN_APP_PATH="dist/win-unpacked/resources/app.asar.unpacked/node-backend"
if [ -d "$WIN_APP_PATH" ]; then
    if [ -d "node-backend/node_modules" ]; then
        log_info "复制 node_modules 到 Windows 应用..."
        cp -R node-backend/node_modules "$WIN_APP_PATH/"
        log_success "Windows 后端依赖复制完成"
    fi
fi

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
