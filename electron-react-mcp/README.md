# ReAct MCP 桌面客户端

基于 Electron 的 ReAct MCP 桌面应用程序，集成了 Spring Boot 后端和 React 前端。

## 🌟 特性

- **一体化桌面应用**：无需分别启动前后端服务
- **自动服务管理**：应用启动时自动启动 Spring Boot 服务
- **跨平台支持**：支持 macOS、Windows、Linux
- **完整的 ReAct 功能**：支持浏览器自动化、数学计算、文件操作等

## 🚀 快速开始

### 开发模式

```bash
# 安装依赖
npm install
cd react-ui && npm install && cd ..

# 启动开发环境（自动启动 Spring Boot 和 Electron）
npm run dev
```

### 启动预构建应用

```bash
# 启动应用（适用于已有构建的应用）
npm start
```

### 构建生产版本

```bash
# 构建 Electron 应用
npm run build-electron
```

构建产物位于 `dist/` 目录中。

## 📁 项目结构

```
electron-react-mcp/
├── main.js                 # Electron 主进程
├── preload.js              # 预加载脚本
├── package.json           # Electron 项目配置
├── react-ui/             # React 前端
│   ├── public/           # 静态资源
│   └── src/              # React 源码
├── spring-boot-server/   # Spring Boot 服务
│   └── react-mcp-demo-0.0.1-SNAPSHOT.jar
├── scripts/              # 构建和启动脚本
│   ├── build-electron.js # 构建脚本
│   └── start-dev.js      # 开发启动脚本
└── dist/                # 构建输出目录
```

## ⚙️ 工作原理

1. **应用启动**：Electron 启动时会检查 Spring Boot 服务是否已在运行
2. **服务启动**：如果服务未运行，则自动启动内嵌的 Spring Boot 应用
3. **前端加载**：加载 React 前端界面
4. **通信机制**：前端通过 IPC 与主进程通信，获取服务状态和信息

## 🔧 开发指南

### 添加新功能

1. 在 Spring Boot 项目中添加新功能
2. 重新构建 JAR 文件并复制到 `spring-boot-server/` 目录
3. 在 React 前端添加相应的 UI 元素

### 调试

- 使用 `npm run dev` 启动开发模式
- 查看终端输出获取服务启动信息
- 使用 Electron 开发者工具调试前端

### 重新构建 Spring Boot 服务

```bash
# 在 react-mcp-demo 目录中
cd ../react-mcp-demo
mvn clean package -DskipTests

# 复制 JAR 文件到 Electron 项目
cp target/react-mcp-demo-0.0.1-SNAPSHOT.jar ../electron-react-mcp/spring-boot-server/
```

## 📦 分发

使用以下命令构建不同平台的应用：

```bash
# macOS
npm run dist -- --mac

# Windows
npm run dist -- --win

# Linux
npm run dist -- --linux
```

### 构建特定格式

```bash
# 构建 dmg (macOS)
npm run dist -- --mac dmg

# 构建 zip (macOS)
npm run dist -- --mac zip

# 构建 NSIS 安装程序 (Windows)
npm run dist -- --win nsis
```

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来改进这个项目！

## 📄 许可证

MIT