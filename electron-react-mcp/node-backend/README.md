# Node.js ReAct MCP Backend

基于 Node.js 的 ReAct Agent 后端服务，完整复刻 Spring Boot 版本的所有功能。

## 架构设计

```
node-mcp-backend/
├── package.json              # 项目配置和依赖
├── .env                      # 环境变量（不提交）
├── .env.example              # 环境变量模板
└── src/
    ├── server.js             # Express 服务器 + HTTP 路由
    ├── config/
    │   └── llmConfig.js      # LLM 提供商配置（Qwen/OpenAI）
    ├── llm/
    │   └── llmClient.js      # LLM API 调用 + 错误处理
    ├── agent/
    │   └── reactAgent.js     # ReAct Agent 核心引擎
    ├── event/
    │   └── reactEventPublisher.js  # 事件发布订阅系统
    ├── model/
    │   └── reactStepEvent.js       # 事件模型定义
    └── tools/
        ├── toolRegistry.js          # 工具注册器
        ├── mathTools.js             # 数学运算工具
        ├── fileSystemTools.js       # 文件系统工具
        ├── documentReaderTools.js   # 文档读取工具（Word/Excel）
        └── playwrightTools.js       # 浏览器自动化工具
```

## 功能特性

### ✅ 完整功能支持

- **多 LLM 提供商**：支持 Qwen 和 OpenAI，可通过配置文件切换
- **ReAct 框架**：完整的推理与行动循环（Thought → Action → Observation）
- **流式输出**：基于 SSE 的实时事件推送
- **完整工具集**：
  - 数学运算（加减乘除、平方根）
  - 文件系统操作（读写文件、目录管理）
  - 文档解析（Word、Excel）
  - 浏览器自动化（Playwright 远程模式）
- **错误处理**：
  - 限流自动重试（指数退避）
  - 内容审查异常处理
  - 消息序列自动修复
- **智能压缩**：超长文本三段式采样，避免 token 超限

## 快速开始

### 1. 安装依赖

```bash
cd node-mcp-backend
npm install
```

### 2. 配置环境变量

复制 `.env.example` 为 `.env`，并填入真实 API Key：

```bash
cp .env.example .env
```

编辑 `.env` 文件：

```bash
# 选择 LLM 提供商：qwen 或 openai
LLM_PROVIDER=qwen

# Qwen 配置
QWEN_API_KEY=sk-your-real-api-key-here
QWEN_MODEL_NAME=qwen-turbo

# OpenAI 配置（或私有化部署的 OpenAI 协议接口）
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_API_KEY=sk-your-openai-key-here
OPENAI_MODEL_NAME=gpt-4o-mini

# 最大消息历史数量
MAX_MESSAGES=10
```

### 3. 启动服务

```bash
npm start
```

或开发模式（自动重启）：

```bash
npm run dev
```

服务启动后会显示：

```
╔════════════════════════════════════════════════════════╗
║  Node.js ReAct MCP Backend                             ║
╠════════════════════════════════════════════════════════╣
║  服务器已启动                                           ║
║  端口: 8080                                             ║
║  LLM 提供商: QWEN                                       ║
║  模型: qwen-turbo                                       ║
╚════════════════════════════════════════════════════════╝
```

## API 接口

### 1. 健康检查

```http
GET /health
```

响应：
```json
{
  "status": "ok",
  "provider": "qwen",
  "timestamp": "2024-12-16T10:30:00.000Z"
}
```

### 2. 同步执行任务

```http
GET /react/solve?task=计算12+8
```

响应：
```json
{
  "result": "12 + 8 = 20"
}
```

### 3. 流式执行任务（SSE）

```http
GET /react/solve-stream?task=计算12+8
```

响应流：
```
event: thought
data: {"type":"thought","stepNumber":1,"content":"需要使用加法工具..."}

event: action
data: {"type":"action","stepNumber":1,"content":"调用工具: add(12, 8)"}

event: observation
data: {"type":"observation","stepNumber":1,"content":"计算结果: 12 + 8 = 20"}

event: final_answer
data: {"type":"final_answer","content":"12 + 8 = 20"}
```

### 4. 获取配置信息

```http
GET /config
```

响应：
```json
{
  "provider": "qwen",
  "maxMessages": 10,
  "modelName": "qwen-turbo"
}
```

## 与 Electron 客户端集成

Node 后端完全兼容现有 Electron 客户端的接口协议：

1. **修改客户端后端地址**：将 Spring Boot 的 `http://localhost:8080` 改为 Node 后端地址
2. **保持接口不变**：`/react/solve` 和 `/react/solve-stream` 接口签名完全一致
3. **事件格式兼容**：SSE 事件类型和数据结构保持一致

## 技术栈对比

| 功能 | Spring Boot 版本 | Node.js 版本 |
|------|----------------|-------------|
| LLM 调用 | langchain4j | axios |
| HTTP 服务器 | Spring Web | Express |
| 文档解析 | Apache POI | mammoth + xlsx |
| 浏览器自动化 | Playwright Java | Playwright (远程模式) |
| 流式输出 | SseEmitter | SSE (原生) |
| 配置管理 | application.yml | .env |

## 优势

- ✅ **更轻量**：无需 JRE，安装包体积更小（约节省 50-100MB）
- ✅ **启动更快**：100-500ms vs 0.8-2s
- ✅ **生态丰富**：npm 包更新更快，社区更活跃
- ✅ **开发效率**：异步编程更自然，调试更方便
- ✅ **部署简单**：单一进程，资源占用更低

## 开发说明

### 添加新工具

1. 在 `src/tools/` 下创建新的工具类
2. 实现 `getToolDefinitions()` 和 `executeTool()` 方法
3. 在 `toolRegistry.js` 中注册

### 切换 LLM 提供商

修改 `.env` 中的 `LLM_PROVIDER` 为 `qwen` 或 `openai`。

### 调整模型参数

在 `llmClient.js` 的 `chat()` 方法中修改 `temperature` 等参数。

## 注意事项

1. **API Key 安全**：永远不要提交 `.env` 文件到代码仓库
2. **端口冲突**：默认端口 8080，如果冲突可通过环境变量 `PORT` 修改
3. **浏览器自动化**：需要 Electron 客户端启动并开启 CDP 端口 9222
4. **内存管理**：会话历史限制为 10 条消息，避免内存溢出

## 许可证

MIT
