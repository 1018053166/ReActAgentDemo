#!/bin/bash

###############################################################################
# ReAct MCP 客户端一键打包脚本
# 功能：自动完成从准备到打包的全流程
###############################################################################

set -e  # 遇到错误立即退出

echo "╔════════════════════════════════════════════════════════╗"
echo "║  ReAct MCP 客户端一键打包                               ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$( dirname "$SCRIPT_DIR" )"
BACKEND_DIR="$PROJECT_DIR/../node-mcp-backend"

echo -e "${BLUE}📁 项目目录: $PROJECT_DIR${NC}"
echo -e "${BLUE}📁 后端目录: $BACKEND_DIR${NC}"
echo ""

# 步骤 1: 安装客户端依赖
echo -e "${GREEN}📦 [1/5] 安装 Electron 客户端依赖...${NC}"
cd "$PROJECT_DIR"
npm install
echo ""

# 步骤 2: 准备 Node.js 后端
echo -e "${GREEN}📦 [2/5] 准备 Node.js 后端...${NC}"
npm run build:electron
echo ""

# 步骤 3: 选择打包平台
echo -e "${BLUE}请选择打包平台:${NC}"
echo "  1) Mac (dmg)"
echo "  2) Windows (exe + zip)"
echo "  3) 所有平台"
echo "  4) 仅准备（不打包）"
read -p "请输入选项 [1-4]: " choice

case $choice in
  1)
    echo -e "${GREEN}📦 [3/5] 打包 macOS 版本...${NC}"
    npm run dist:mac
    ;;
  2)
    echo -e "${GREEN}📦 [3/5] 打包 Windows 版本...${NC}"
    npm run dist:win
    ;;
  3)
    echo -e "${GREEN}📦 [3/5] 打包所有平台...${NC}"
    npm run dist:all
    ;;
  4)
    echo -e "${GREEN}✅ 准备完成，跳过打包${NC}"
    exit 0
    ;;
  *)
    echo -e "${RED}❌ 无效选项${NC}"
    exit 1
    ;;
esac

# 步骤 4: 显示打包结果
echo ""
echo -e "${GREEN}📦 [4/5] 检查打包结果...${NC}"
if [ -d "$PROJECT_DIR/dist" ]; then
  echo -e "${BLUE}📁 输出目录: $PROJECT_DIR/dist${NC}"
  ls -lh "$PROJECT_DIR/dist"
else
  echo -e "${RED}❌ 未找到 dist 目录${NC}"
  exit 1
fi

# 步骤 5: 完成
echo ""
echo "╔════════════════════════════════════════════════════════╗"
echo "║  ✅ 打包完成！                                          ║"
echo "╠════════════════════════════════════════════════════════╣"
echo "║  安装包位置:                                            ║"
echo "║  - macOS: dist/*.dmg                                   ║"
echo "║  - Windows: dist/*.exe 或 dist/*.zip                   ║"
echo "╠════════════════════════════════════════════════════════╣"
echo "║  运行要求:                                              ║"
echo "║  - 需要配置 Qwen API Key 在环境变量中                   ║"
echo "║  - DASHSCOPE_API_KEY=your_api_key                      ║"
echo "╚════════════════════════════════════════════════════════╝"
