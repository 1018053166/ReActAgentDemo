# Playwright MCP 本地集成指南

## 概述

本项目已成功集成本地版 Playwright MCP 工具，支持通过 AI Agent 控制浏览器进行自动化操作。

## 架构说明

```
┌─────────────────────────────────────────────────────────────┐
│  用户请求（自然语言）                                        │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  AgentController (REST API)                                 │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  McpAssistant (ReAct Agent)                                 │
│  - Qwen 模型推理                                            │
│  - ReAct 循环（Thought → Action → Observation）            │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  PlaywrightMcpTools (浏览器控制工具)                        │
│  - navigate: 打开网页                                       │
│  - click: 点击元素                                          │
│  - fill: 输入文本                                           │
│  - getText: 获取文本                                        │
│  - screenshot: 截图                                         │
│  - evaluate: 执行 JS                                        │
│  - ... 更多工具                                             │
└──────────────────────────┬──────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Playwright Engine (1.49.0)                                 │
│  - Chromium/Firefox/WebKit                                  │
│  - 本地浏览器实例                                           │
└─────────────────────────────────────────────────────────────┘
```

## 核心功能

### 1. 自动化浏览器操作

支持的操作类型：
- ✅ 网页导航
- ✅ 元素定位与点击
- ✅ 表单填写
- ✅ 文本内容提取
- ✅ 页面截图
- ✅ JavaScript 执行
- ✅ 等待与同步

### 2. AI 智能推理

通过 Qwen 模型的 ReAct 能力：
1. **理解** 用户的自然语言指令
2. **规划** 执行步骤（Thought）
3. **调用** Playwright 工具（Action）
4. **观察** 执行结果（Observation）
5. **推理** 下一步操作
6. **返回** 最终答案

## 使用示例

### 示例 1：简单网页访问

**请求：**
```bash
curl "http://localhost:8080/react/solve?task=打开百度首页并获取页面标题"
```

**AI 推理过程：**
```
Thought: 我需要先打开百度首页，然后获取页面标题
Action: navigate[{"url":"https://www.baidu.com","headless":false}]
Observation: 成功打开页面: https://www.baidu.com (标题: 百度一下，你就知道)
Thought: 页面已成功打开，我已经获得了标题信息
Final Answer: 百度首页的标题是：百度一下，你就知道
```

### 示例 2：自动搜索

**请求：**
```bash
curl "http://localhost:8080/react/solve?task=在百度搜索 Spring Boot 教程"
```

**AI 推理过程：**
```
Thought: 需要先打开百度，然后在搜索框输入关键词，最后点击搜索按钮
Action: navigate[{"url":"https://www.baidu.com","headless":false}]
Observation: 成功打开页面
Action: fill[{"selector":"#kw","text":"Spring Boot 教程"}]
Observation: 成功在搜索框中输入内容
Action: click[{"selector":"#su"}]
Observation: 成功点击搜索按钮
Final Answer: 已在百度成功搜索"Spring Boot 教程"
```

### 示例 3：数据抓取

**请求：**
```bash
curl "http://localhost:8080/react/solve?task=访问 example.com 并提取页面中所有段落文本"
```

**AI 推理过程：**
```
Thought: 先打开目标网页，然后获取段落内容
Action: navigate[{"url":"https://example.com","headless":true}]
Observation: 成功打开页面
Action: getText[{"selector":"p"}]
Observation: 元素文本内容: This domain is for use in illustrative examples...
Final Answer: 页面段落内容为：...
```

### 示例 4：执行 JavaScript

**请求：**
```bash
curl "http://localhost:8080/react/solve?task=打开百度并统计页面中有多少个链接"
```

**AI 推理过程：**
```
Thought: 先打开百度，然后执行 JS 代码统计链接数量
Action: navigate[{"url":"https://www.baidu.com","headless":true}]
Observation: 成功打开页面
Action: evaluate[{"script":"document.querySelectorAll('a').length"}]
Observation: JavaScript 执行结果: 42
Final Answer: 百度首页共有 42 个链接
```

## 工具 API 参考

### navigate
打开指定网页

**参数：**
- `url` (String): 目标 URL
- `headless` (Boolean): 是否无头模式，默认 false

**示例：**
```json
{"url": "https://www.baidu.com", "headless": false}
```

### click
点击页面元素

**参数：**
- `selector` (String): CSS 选择器、文本选择器或其他支持的选择器

**示例：**
```json
{"selector": "#submit-button"}
{"selector": "text=登录"}
{"selector": "button:has-text('确定')"}
```

### fill
在输入框中填写内容

**参数：**
- `selector` (String): 输入框选择器
- `text` (String): 要输入的文本

**示例：**
```json
{"selector": "#username", "text": "admin"}
```

### getText
获取元素的文本内容

**参数：**
- `selector` (String): 元素选择器

**返回：**
元素的文本内容

### screenshot
截取页面截图

**参数：**
- `path` (String, 可选): 保存路径，如不提供则返回 base64

**示例：**
```json
{"path": "/tmp/screenshot.png"}
```

### waitTime
等待指定时间

**参数：**
- `milliseconds` (int): 等待的毫秒数

**示例：**
```json
{"milliseconds": 2000}
```

### getPageInfo
获取当前页面信息（URL 和标题）

**参数：** 无

### closeBrowser
关闭浏览器并释放资源

**参数：** 无

### evaluate
在页面上下文中执行 JavaScript 代码

**参数：**
- `script` (String): JavaScript 代码

**示例：**
```json
{"script": "window.location.href"}
{"script": "document.title"}
{"script": "document.querySelectorAll('img').length"}
```

## 配置选项

### 有头模式 vs 无头模式

**有头模式（headless=false）：**
- 优点：可视化调试，实时查看操作过程
- 缺点：性能稍慢，需要图形界面
- 适用：开发、调试、演示

**无头模式（headless=true）：**
- 优点：性能更好，无需图形界面，适合服务器
- 缺点：无法实时观察
- 适用：生产环境、自动化测试、后台任务

### 浏览器选择

当前默认使用 Chromium，可修改为：
- Firefox: `playwright.firefox().launch()`
- WebKit: `playwright.webkit().launch()`

## 高级用法

### 1. 复杂交互流程

**任务：** 登录网站并提取用户信息

```bash
curl "http://localhost:8080/react/solve?task=访问登录页面，输入用户名admin密码123456，点击登录，然后获取用户个人信息"
```

AI 会自动分解为多个步骤执行。

### 2. 条件判断

**任务：** 检查元素是否存在

```bash
curl "http://localhost:8080/react/solve?task=打开网页，检查是否有错误提示信息"
```

### 3. 循环操作

**任务：** 遍历列表

```bash
curl "http://localhost:8080/react/solve?task=访问新闻网站，获取首页所有新闻标题"
```

## 资源管理

### 自动初始化
应用启动时，`@PostConstruct` 方法会自动初始化 Playwright 实例。

### 自动清理
应用关闭时，`@PreDestroy` 方法会自动清理浏览器资源。

### 手动控制
可通过 `closeBrowser` 工具手动关闭浏览器：
```bash
curl "http://localhost:8080/react/solve?task=关闭浏览器"
```

## 故障排查

### 问题 1：浏览器启动失败

**症状：** 日志显示 "Playwright 初始化失败"

**解决方案：**
```bash
# 安装浏览器
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
```

### 问题 2：元素选择器不正确

**症状：** "点击失败: Timeout waiting for selector"

**解决方案：**
- 使用浏览器开发者工具查看正确的选择器
- 尝试使用文本选择器：`text=按钮文字`
- 添加等待时间：先调用 `waitTime` 再操作

### 问题 3：无头模式下截图为空

**症状：** 截图保存成功但内容为空白

**解决方案：**
- 在截图前添加 `waitTime` 等待页面加载完成
- 检查页面 URL 是否正确

## 性能优化

### 1. 复用浏览器实例
避免频繁启动和关闭浏览器，一次会话中复用同一实例。

### 2. 使用无头模式
生产环境建议使用无头模式以提升性能。

### 3. 合理设置超时时间
根据网络状况调整页面加载超时时间。

## 安全建议

### 1. 输入验证
不要直接将用户输入作为 URL 或脚本执行。

### 2. 限制可访问域名
在生产环境中限制可访问的域名白名单。

### 3. 资源限制
设置浏览器实例数量上限，避免资源耗尽。

## 与传统 Playwright 的对比

| 特性 | 传统 Playwright | Playwright MCP (本项目) |
|------|----------------|------------------------|
| 使用方式 | 编写测试代码 | 自然语言指令 |
| 学习曲线 | 需要学习 API | 零门槛 |
| 灵活性 | 完全可控 | AI 自主推理 |
| 适用场景 | 固定测试流程 | 动态任务、探索性操作 |
| 可预测性 | 100% 可预测 | 取决于 AI 推理质量 |
| 调试难度 | 容易 | 中等（需查看 ReAct 日志） |

## 最佳实践

### 1. 清晰的任务描述
```bash
# ✅ 好的描述
"打开百度，搜索 Java 教程，然后截图保存到 /tmp/result.png"

# ❌ 模糊的描述
"搜索点东西"
```

### 2. 分步验证
对于复杂任务，可以分步骤验证：
```bash
# 步骤 1
curl "...?task=打开目标网页"

# 步骤 2
curl "...?task=在当前页面点击登录按钮"
```

### 3. 查看 ReAct 日志
启用 DEBUG 日志查看 AI 的推理过程：
```yaml
logging:
  level:
    com.example.reactmcp: DEBUG
    dev.langchain4j: DEBUG
```

## 后续扩展

### 计划功能
- [ ] 支持多标签页管理
- [ ] 支持文件上传/下载
- [ ] 支持 Cookie 管理
- [ ] 支持网络拦截和模拟
- [ ] 支持移动设备仿真
- [ ] 集成视觉识别能力

## 参考资源

- [Playwright 官方文档](https://playwright.dev/java/)
- [langchain4j 文档](https://docs.langchain4j.dev/)
- [ReAct 论文](https://arxiv.org/abs/2210.03629)
- [MCP 协议规范](https://modelcontextprotocol.io/)

## 许可证

Apache-2.0
