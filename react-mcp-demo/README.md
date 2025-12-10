# React + MCP Demo（Spring Boot + langchain4j + Qwen）

基于 Spring Boot + langchain4j 实现的 ReAct Agent 与 MCP 工具集成示例。

## 技术栈
- Spring Boot 3.5.8
- langchain4j 0.35.0
- langchain4j-dashscope（通义千问 Qwen）
- Playwright 1.49.0（本地浏览器自动化）
- Java 17

## 项目结构
```
react-mcp-demo/
├── src/main/java/com/example/reactmcp/
│   ├── ReactMcpApplication.java          # 启动类
│   ├── agent/
│   │   └── McpAssistant.java             # ReAct Agent 接口（含系统提示）
│   ├── tools/
│   │   ├── McpTools.java                 # 基础数学工具
│   │   ├── FileSystemTools.java          # 文件系统工具
│   │   ├── DocumentReaderTools.java      # 文档读取工具
│   │   └── PlaywrightMcpTools.java       # 浏览器自动化工具（新增）
│   ├── config/
│   │   └── LangchainConfig.java          # langchain4j 配置（模型 + Agent）
│   └── web/
│       └── AgentController.java          # REST 接口
└── pom.xml
```

## 快速开始

### 1. 安装 Playwright 浏览器（首次使用）

```bash
# 使用 Maven 安装 Playwright 浏览器
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
```

### 2. 构建项目并下载依赖
```bash
cd react-mcp-demo
./mvnw clean install
# 或
mvn clean install
```

### 3. 启动应用
```bash
./mvnw spring-boot:run
# 或
mvn spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

### 4. 调用示例

#### Playwright 浏览器自动化示例
```bash
# 示例 1: 打开网页并获取标题
curl "http://localhost:8080/react/solve?task=访问 https://example.com 并获取页面标题"

# 示例 2: 自动搜索
curl "http://localhost:8080/react/solve?task=在百度搜索 Spring Boot 教程"

# 示例 3: 网页截图
curl "http://localhost:8080/react/solve?task=打开百度首页，截图保存到 /tmp/baidu.png"

# 或使用快速测试脚本
./test-playwright-mcp.sh
```

#### 数学计算示例
```bash
curl "http://localhost:8080/react/solve?task=计算 123 加 456 等于多少"
```

模型会按 **Thought → Action → Observation → ... → Final Answer** 流程工作：
- 调用 MCP 工具（queryServiceRegistry、fetchGatewayLogs、evaluatePolicy、suggestProtocolConversion）
- 根据工具返回的观察结果继续推理
- 最终给出根因分析与修复建议

## MCP 工具说明

### 1. Playwright 浏览器自动化工具（本地版 MCP）

本项目已集成本地版 Playwright MCP 工具，支持 AI 控制浏览器进行自动化操作：

| 工具名 | 功能 | 参数 | 示例 |
|-------|------|------|------|
| `navigate` | 启动浏览器并打开网页 | `url`: 网址<br>`headless`: 是否无头模式 | 打开百度首页 |
| `click` | 点击页面元素 | `selector`: 元素选择器 | 点击登录按钮 |
| `fill` | 在输入框中输入文本 | `selector`: 输入框选择器<br>`text`: 输入内容 | 填写搜索关键词 |
| `getText` | 获取元素文本内容 | `selector`: 元素选择器 | 读取标题文字 |
| `screenshot` | 截取页面截图 | `path`: 保存路径（可选） | 保存页面截图 |
| `waitTime` | 等待指定时间 | `milliseconds`: 毫秒数 | 等待页面加载 |
| `getPageInfo` | 获取当前页面信息 | 无 | 查看当前 URL |
| `closeBrowser` | 关闭浏览器 | 无 | 释放资源 |
| `evaluate` | 执行 JavaScript 代码 | `script`: JS 代码 | 获取元素属性 |

#### 使用示例

**示例 1：自动搜索**
```bash
curl "http://localhost:8080/react/solve?task=帮我打开百度，搜索 Spring Boot 教程"
```

**示例 2：网页数据抓取**
```bash
curl "http://localhost:8080/react/solve?task=访问 https://example.com 并获取页面标题"
```

**示例 3：自动化测试**
```bash
curl "http://localhost:8080/react/solve?task=打开豆包网站 doubao.com，向豆包提问今天杭州天气如何"
```

#### 特性
- ✅ **100% 本地运行**：无需依赖云服务
- ✅ **AI 智能控制**：通过自然语言指令操作浏览器
- ✅ **支持有头/无头模式**：可视化调试或后台运行
- ✅ **跨浏览器支持**：Chromium/Firefox/WebKit
- ✅ **资源自动管理**：启动时初始化，关闭时自动清理

### 2. 其他工具集

当前还包含其他工具（详见对应文件）：

#### 基础数学工具（McpTools.java）
| 工具名 | 功能 | 参数 |
|-------|------|------|
| `add` | 两数相加 | `a`, `b` |
| `subtract` | 两数相减 | `a`, `b` |
| `multiply` | 两数相乘 | `a`, `b` |
| `divide` | 两数相除 | `a`, `b` |

## ReAct 流程示意
```
Question: 定位服务不可用根因
  ↓
Thought: 先查注册中心实例状态
  ↓
Action: queryServiceRegistry[{"serviceName":"order-service"}]
  ↓
Observation: {"instances":2,"healthy":1,"unhealthy":1}
  ↓
Thought: 有 1 个实例不健康，再看网关日志
  ↓
Action: fetchGatewayLogs[{"serviceName":"order-service","timeRange":"30min"}]
  ↓
Observation: circuitOpen=true, rateLimit=120/s, errors=23
  ↓
Thought: 熔断已触发，限流错误 23 次
  ↓
Final Answer: 根因是熔断器打开 + 限流触发。建议：1. 排查上游流量激增原因；2. 重启不健康实例；3. 调整限流阈值或扩容。
```

## 配置说明

### 模型配置
在 [LangchainConfig.java](src/main/java/com/example/reactmcp/config/LangchainConfig.java) 中：
- 模型：`qwen-max`（通义千问最大版本）
- API Key：已配置为 `sk-your-real-qwen-api-key-here`

### 系统提示
在 [McpAssistant.java](src/main/java/com/example/reactmcp/agent/McpAssistant.java) 的 `@SystemMessage` 中定义：
- 要求按 Thought/Action/Observation 格式输出
- 限制最大步数为 6
- 仅使用白名单工具
- 遇到不确定先验证再下结论

## 后续改进建议
1. **替换模拟数据**：将 `McpTools` 中的方法改为调用真实 MCP API（注册中心、网关、策略中心等）。
2. **安全加固**：API Key 放到环境变量或配置中心；工具调用加权限校验。
3. **观测与审计**：记录 Thought/Action/Observation 轨迹到日志或存储，便于复盘。
4. **错误重试**：在工具调用失败时自动重试或降级。
5. **扩展工具集**：根据 MCP 平台能力增加协议转换、搜索、事故库等工具。

## 常见问题
**Q1：编译错误"cannot be resolved"？**
- 运行 `mvn clean install` 下载依赖。

**Q2：如何换成其他模型（如 OpenAI）？**
- 修改 `LangchainConfig.chatLanguageModel()`，替换为 `OpenAiChatModel`，并配置对应 API Key。

**Q3：如何查看 Agent 执行日志？**
- 在 `application.yml` 添加：
```yaml
logging:
  level:
    dev.langchain4j: DEBUG
```

## 许可
Apache-2.0
