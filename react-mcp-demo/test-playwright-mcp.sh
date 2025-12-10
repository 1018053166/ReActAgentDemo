#!/bin/bash

# Playwright MCP 快速验证脚本

echo "=================================================="
echo "  Playwright MCP 本地集成验证工具"
echo "=================================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 检查服务是否运行
check_service() {
    echo -n "检查服务状态... "
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 服务运行中${NC}"
        return 0
    else
        echo -e "${RED}✗ 服务未启动${NC}"
        echo ""
        echo "请先启动应用："
        echo "  mvn spring-boot:run"
        echo ""
        return 1
    fi
}

# 测试用例
run_test() {
    local test_name=$1
    local task=$2
    
    echo ""
    echo "=================================================="
    echo "测试：${test_name}"
    echo "=================================================="
    echo ""
    echo "任务描述：${task}"
    echo ""
    echo -e "${YELLOW}执行中...${NC}"
    echo ""
    
    # 执行请求并显示结果
    response=$(curl -s "http://localhost:8080/react/solve?task=${task}")
    
    echo "响应结果："
    echo "$response"
    echo ""
}

# 主流程
main() {
    echo "开始验证 Playwright MCP 集成..."
    echo ""
    
    # 检查服务
    if ! check_service; then
        exit 1
    fi
    
    echo ""
    echo "选择要运行的测试："
    echo "  1) 测试 1：打开网页并获取标题"
    echo "  2) 测试 2：执行数学计算"
    echo "  3) 测试 3：网页导航和截图"
    echo "  4) 测试 4：执行 JavaScript"
    echo "  5) 运行所有测试"
    echo "  0) 退出"
    echo ""
    read -p "请输入选项 (0-5): " choice
    
    case $choice in
        1)
            run_test "打开网页获取标题" "访问 https://example.com 并获取页面标题"
            ;;
        2)
            run_test "数学计算" "计算 123 加 456 等于多少"
            ;;
        3)
            run_test "网页截图" "打开百度首页，截图保存到 /tmp/baidu.png"
            ;;
        4)
            run_test "执行 JavaScript" "打开 example.com 并使用 JavaScript 获取页面 URL"
            ;;
        5)
            echo ""
            echo "运行所有测试..."
            run_test "测试1：打开网页获取标题" "访问 https://example.com 并获取页面标题"
            run_test "测试2：数学计算" "计算 123 加 456 等于多少"
            run_test "测试3：网页截图" "打开百度首页，截图保存到 /tmp/baidu.png"
            run_test "测试4：执行 JavaScript" "打开 example.com 并使用 JavaScript 获取页面 URL"
            ;;
        0)
            echo "退出"
            exit 0
            ;;
        *)
            echo -e "${RED}无效的选项${NC}"
            exit 1
            ;;
    esac
    
    echo ""
    echo "=================================================="
    echo "测试完成！"
    echo "=================================================="
    echo ""
    echo "提示："
    echo "  - 查看应用日志可以看到 ReAct 推理过程"
    echo "  - 更多示例请参考 PLAYWRIGHT_MCP_GUIDE.md"
    echo ""
}

# 运行主流程
main
