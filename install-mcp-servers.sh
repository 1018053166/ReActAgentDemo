#!/bin/bash

# MCP 服务器安装配置脚本
# 用途：自动安装和配置 context7 和顺序思考 MCP 服务器
# 作者：Claude Code
# 创建日期：2025-11-13

set -e  # 遇到错误立即退出

echo "================================================"
echo "    MCP 服务器自动安装配置脚本"
echo "================================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Node.js 是否已安装
echo "检查 Node.js 环境..."
if ! command -v node &> /dev/null; then
    echo -e "${RED}❌ Node.js 未安装！请先安装 Node.js${NC}"
    exit 1
fi

# 获取 Node.js 路径（支持 nvm）
NODE_PATH=$(which node)
NPM_PATH=$(which npm)
NODE_VERSION=$(node -v)

echo -e "${GREEN}✓ 检测到 Node.js ${NODE_VERSION}${NC}"
echo "  Node 路径: $NODE_PATH"
echo "  NPM  路径: $NPM_PATH"
echo ""

# 获取全局 node_modules 路径
GLOBAL_NODE_MODULES=$($NPM_PATH root -g)
echo "全局模块路径: $GLOBAL_NODE_MODULES"
echo ""

# 步骤 1: 安装 MCP 服务器包
echo "================================================"
echo "步骤 1: 安装 MCP 服务器包"
echo "================================================"
echo ""

# 安装 context7 MCP
echo "正在安装 @upstash/context7-mcp..."
if $NPM_PATH install -g @upstash/context7-mcp; then
    echo -e "${GREEN}✓ context7-mcp 安装成功${NC}"
else
    echo -e "${YELLOW}⚠ context7-mcp 可能已安装或安装失败${NC}"
fi
echo ""

# 安装顺序思考 MCP
echo "正在安装 @modelcontextprotocol/server-sequential-thinking..."
if $NPM_PATH install -g @modelcontextprotocol/server-sequential-thinking; then
    echo -e "${GREEN}✓ sequential-thinking 安装成功${NC}"
else
    echo -e "${YELLOW}⚠ sequential-thinking 可能已安装或安装失败${NC}"
fi
echo ""

# 步骤 2: 配置 MCP 服务器
echo "================================================"
echo "步骤 2: 配置 MCP 服务器到 Claude Code"
echo "================================================"
echo ""

# 检查 Claude CLI 是否可用
if ! command -v claude &> /dev/null; then
    echo -e "${RED}❌ Claude CLI 未找到！请确保 Claude Code 已安装${NC}"
    exit 1
fi

# 构建服务器脚本路径
CONTEXT7_PATH="$GLOBAL_NODE_MODULES/@upstash/context7-mcp/dist/index.js"
SEQUENTIAL_THINKING_PATH="$GLOBAL_NODE_MODULES/@modelcontextprotocol/server-sequential-thinking/dist/index.js"

# 验证文件是否存在
echo "验证服务器文件..."
if [ ! -f "$CONTEXT7_PATH" ]; then
    echo -e "${RED}❌ context7-mcp 脚本未找到: $CONTEXT7_PATH${NC}"
    exit 1
fi

if [ ! -f "$SEQUENTIAL_THINKING_PATH" ]; then
    echo -e "${RED}❌ sequential-thinking 脚本未找到: $SEQUENTIAL_THINKING_PATH${NC}"
    exit 1
fi

echo -e "${GREEN}✓ 服务器文件验证成功${NC}"
echo ""

# 添加 context7 MCP 服务器
echo "添加 context7 MCP 服务器..."
claude mcp add --transport stdio context7 -- "$NODE_PATH" "$CONTEXT7_PATH"
echo -e "${GREEN}✓ context7 配置成功${NC}"
echo ""

# 添加顺序思考 MCP 服务器
echo "添加 sequential-thinking MCP 服务器..."
claude mcp add --transport stdio sequential-thinking -- "$NODE_PATH" "$SEQUENTIAL_THINKING_PATH"
echo -e "${GREEN}✓ sequential-thinking 配置成功${NC}"
echo ""

# 步骤 3: 验证配置
echo "================================================"
echo "步骤 3: 验证 MCP 服务器配置"
echo "================================================"
echo ""

echo "检查已配置的 MCP 服务器..."
claude mcp list

echo ""
echo "================================================"
echo -e "${GREEN}🎉 MCP 服务器安装配置完成！${NC}"
echo "================================================"
echo ""
echo "已安装的服务器:"
echo "  • context7 - 提供最新的编程库文档和开发工具"
echo "  • sequential-thinking - 增强逻辑推理和问题解决能力"
echo ""
echo "使用方法:"
echo "  1. 在 Claude Code 中运行 /mcp 查看可用服务器"
echo "  2. 服务器将自动为 Claude 提供额外功能"
echo ""
echo "注意事项:"
echo "  • 如需重新配置，请先运行: claude mcp remove <服务器名>"
echo "  • 查看服务器状态: claude mcp list"
echo "  • 本脚本路径: $0"
echo ""