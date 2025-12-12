#!/bin/bash

###############################################################################
# ReAct MCP 客户端打包脚本
# 功能：后端编译 → JAR 复制 → 客户端打包 → 生成安装包
# 用法：
#   ./build-package.sh              # 完整流程（编译后端 + 打包客户端）
#   ./build-package.sh --skip-backend  # 跳过后端编译（仅打包客户端）
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
BACKEND_DIR="$PROJECT_ROOT/react-mcp-demo"
FRONTEND_DIR="$PROJECT_ROOT/electron-react-mcp"
JAR_NAME="react-mcp-demo-0.0.1-SNAPSHOT.jar"

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
if [[ "$1" == "--skip-backend" ]]; then
    SKIP_BACKEND=true
    log_warning "跳过后端编译，直接使用现有 JAR 文件"
fi

# 打印横幅
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         ReAct MCP 客户端打包脚本                              ║"
echo "║         Electron + Spring Boot 一体化打包                     ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

###############################################################################
# 步骤 1: 编译 Spring Boot 后端（可选）
###############################################################################
if [ "$SKIP_BACKEND" = false ]; then
    log_info "步骤 1/4: 编译 Spring Boot 后端..."
    cd "$BACKEND_DIR"
    
    if [ ! -f "pom.xml" ]; then
        log_error "未找到 pom.xml 文件，请检查后端项目路径"
        exit 1
    fi
    
    log_info "执行 Maven 编译: mvn clean package -DskipTests"
    mvn clean package -DskipTests
    
    if [ ! -f "target/$JAR_NAME" ]; then
        log_error "JAR 文件编译失败，未找到 target/$JAR_NAME"
        exit 1
    fi
    
    log_success "后端编译完成: target/$JAR_NAME"
    
    ###############################################################################
    # 步骤 2: 复制 JAR 到客户端目录
    ###############################################################################
    log_info "步骤 2/4: 复制 JAR 文件到客户端项目..."
    TARGET_DIR="$FRONTEND_DIR/spring-boot-server"
    
    # 确保目标目录存在
    mkdir -p "$TARGET_DIR"
    
    # 复制 JAR 文件
    cp "target/$JAR_NAME" "$TARGET_DIR/"
    
    if [ -f "$TARGET_DIR/$JAR_NAME" ]; then
        log_success "JAR 文件已复制到: $TARGET_DIR/$JAR_NAME"
    else
        log_error "JAR 文件复制失败"
        exit 1
    fi
else
    log_info "步骤 1/4: 跳过后端编译（使用现有 JAR）"
    log_info "步骤 2/4: 跳过 JAR 复制（使用现有 JAR）"
    
    # 检查 JAR 是否存在
    if [ ! -f "$FRONTEND_DIR/spring-boot-server/$JAR_NAME" ]; then
        log_error "未找到现有 JAR 文件: $FRONTEND_DIR/spring-boot-server/$JAR_NAME"
        log_error "请先编译后端或移除 --skip-backend 参数"
        exit 1
    fi
    
    log_success "使用现有 JAR: $FRONTEND_DIR/spring-boot-server/$JAR_NAME"
fi

###############################################################################
# 步骤 3: 安装客户端依赖（如果需要）
###############################################################################
log_info "步骤 3/4: 检查并安装客户端依赖..."
cd "$FRONTEND_DIR"

if [ ! -d "node_modules" ]; then
    log_info "node_modules 不存在，执行 npm install..."
    npm install
    log_success "依赖安装完成"
else
    log_info "node_modules 已存在，跳过依赖安装"
fi

###############################################################################
# 步骤 4: 打包客户端生成安装包
###############################################################################
log_info "步骤 4/4: 打包 Electron 客户端..."
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
