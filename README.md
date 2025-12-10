# ReAct Agent Demo

基于 ReAct 框架的智能代理系统，集成 Playwright MCP 实现浏览器自动化。

## 项目架构

```
MCP/
├── electron-react-mcp/          # Electron 客户端
│   ├── main.js                  # 主进程
│   ├── preload.js              # 预加载脚本
│   ├── react-ui/               # UI 前端
│   │   └── public/index.html   # 主界面
│   └── spring-boot-server/     # Spring Boot JAR 包
└── react-mcp-demo/             # Spring Boot 后端
    ├── src/main/java/
    │   └── com/example/reactmcp/
    │       ├── agent/          # ReAct Agent 接口
    │       ├── config/         # langchain4j 配置
    │       ├── tools/          # MCP 工具实现
    │       └── web/            # REST API
    └── pom.xml
```

## 技术栈

### 后端
- **Java 17** + **Spring Boot 3.5.8**
- **langchain4j 0.36.2** - AI 编排框架
- **Qwen (通义千问)** - 大语言模型
- **ReAct 框架** - 推理与行动循环
- **Playwright MCP** - 浏览器自动化工具

### 前端
- **Electron** - 跨平台桌面应用
- **HTML5/CSS3/JavaScript** - 原生 Web 技术
- **BrowserView** - 嵌入式浏览器

## 核心功能

### 1. ReAct 智能代理
- 支持思考-行动-观察循环
- 实时流式输出 AI 决策过程
- 完整的工具调用链追踪

### 2. Playwright 浏览器自动化
- **基础操作**：打开网页、点击、输入、截图
- **高级操作**：获取 HTML、控制台日志、鼠标悬停、下拉框选择
- **综合场景**：多页面操作、电商搜索、内容抓取、数据采集

### 3. 数学计算工具
- 加减乘除基础运算

### 4. 文件操作工具
- 列出目录、创建文件、读取文件

## 快速开始

### 环境要求
- Java 17+
- Node.js 14+
- Maven 3.6+

### 后端启动

```bash
cd react-mcp-demo
mvn clean package -DskipTests
java -jar target/react-mcp-demo-0.0.1-SNAPSHOT.jar
```

### 客户端启动

```bash
cd electron-react-mcp
npm install
npm start
```

## 配置说明

### Qwen API Key
在 `react-mcp-demo/src/main/resources/application.properties` 中配置：

```properties
dashscope.api.key=your-api-key-here
```

### 布局配置
- 左侧 30%：控制面板（案例按钮、任务输入、执行日志）
- 右侧 70%：BrowserView 浏览器区域

## API 接口

### 执行任务（流式输出）
```
GET /react/solve-stream?task={任务描述}
```

返回 SSE 事件流：
- `thought`: AI 思考过程
- `action`: 工具调用信息
- `final_answer`: 最终答案

## 架构特点

### 前后端分离
- **Spring Boot**: 纯 API 服务，提供 ReAct 执行引擎
- **Electron**: UI 展示 + BrowserView 管理

### 实时流式输出
- 完整捕获 AI 思考链路
- 实时推送工具执行过程
- SSE 长连接保持

### 原生 BrowserView
- 独立于 UI 主窗口
- 严格三七分布局
- 自主管理浏览能力

## 开发说明

### 修改 UI
直接编辑 `electron-react-mcp/react-ui/public/index.html`，无需重新编译后端。

### 添加工具
1. 在 `PlaywrightMcpTools.java` 中添加 `@Tool` 方法
2. 重新编译 Spring Boot 项目
3. 复制 JAR 到 `electron-react-mcp/spring-boot-server/`

### 调整提示词
在 `LangchainConfig.java` 中修改 `systemMessage`。

## 许可证

内部使用项目
