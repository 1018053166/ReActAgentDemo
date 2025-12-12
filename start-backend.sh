#!/bin/bash

###############################################################################
# Spring Boot 后端编译启动脚本
# 功能：编译后端 JAR → 启动 Spring Boot 服务 → 提供 ReAct API
# 用法：
#   ./start-backend.sh                    # 编译并启动
#   ./start-backend.sh --skip-build       # 跳过编译，直接启动
#   ./start-backend.sh --copy-to-frontend # 编译后复制到客户端目录
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
SERVER_PORT=8080

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
SKIP_BUILD=false
COPY_TO_FRONTEND=false

for arg in "$@"
do
    case $arg in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --copy-to-frontend)
            COPY_TO_FRONTEND=true
            shift
            ;;
    esac
done

# 打印横幅
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         Spring Boot 后端启动脚本                              ║"
echo "║         ReAct Engine + Qwen AI + Playwright Tools             ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

###############################################################################
# 步骤 1: 检查端口占用
###############################################################################
log_info "步骤 1/4: 检查端口 $SERVER_PORT..."

if lsof -Pi :$SERVER_PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
    PID=$(lsof -Pi :$SERVER_PORT -sTCP:LISTEN -t)
    log_warning "端口 $SERVER_PORT 已被占用 (PID: $PID)"
    
    read -p "是否要停止现有服务并重新启动? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "正在停止现有服务 (PID: $PID)..."
        kill -9 $PID
        sleep 2
        log_success "现有服务已停止"
    else
        log_error "端口已被占用，脚本退出"
        exit 1
    fi
else
    log_success "端口 $SERVER_PORT 空闲，可以启动服务"
fi

###############################################################################
# 步骤 2: 编译后端（可选）
###############################################################################
cd "$BACKEND_DIR"

if [ "$SKIP_BUILD" = false ]; then
    log_info "步骤 2/4: 编译 Spring Boot 后端..."
    
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
    JAR_SIZE=$(du -h "target/$JAR_NAME" | cut -f1)
    log_info "JAR 文件大小: $JAR_SIZE"
else
    log_info "步骤 2/4: 跳过后端编译（使用现有 JAR）"
    
    if [ ! -f "target/$JAR_NAME" ]; then
        log_error "未找到现有 JAR 文件: target/$JAR_NAME"
        log_error "请移除 --skip-build 参数以重新编译"
        exit 1
    fi
    
    log_success "使用现有 JAR: target/$JAR_NAME"
fi

###############################################################################
# 步骤 3: 复制到客户端目录（可选）
###############################################################################
if [ "$COPY_TO_FRONTEND" = true ]; then
    log_info "步骤 3/4: 复制 JAR 文件到客户端项目..."
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
    log_info "步骤 3/4: 跳过 JAR 复制（仅启动后端服务）"
fi

###############################################################################
# 步骤 4: 启动 Spring Boot 服务
###############################################################################
log_info "步骤 4/4: 启动 Spring Boot 服务..."

# 检查 Java 版本
if ! command -v java &> /dev/null; then
    log_error "未找到 Java 命令，请安装 Java 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 17 ]; then
    log_warning "Java 版本为 $JAVA_VERSION，建议使用 Java 17+"
fi

log_info "Java 版本: $(java -version 2>&1 | head -1)"

# 启动服务
log_info "执行启动命令: java -jar target/$JAR_NAME"
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                 后端服务正在启动...                           ║"
echo "║                                                                ║"
echo "║  服务地址: http://localhost:$SERVER_PORT                          ║"
echo "║  健康检查: http://localhost:$SERVER_PORT/actuator/health          ║"
echo "║  ReAct API: http://localhost:$SERVER_PORT/react/solve-stream       ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

cd "$BACKEND_DIR"
java -jar "target/$JAR_NAME"

###############################################################################
# 说明：java -jar 会阻塞终端，服务日志将在此输出
# 使用 Ctrl+C 停止服务
###############################################################################
