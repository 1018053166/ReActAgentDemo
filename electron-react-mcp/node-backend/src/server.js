const express = require('express');
const cors = require('cors');
const { ReActAgent } = require('./agent/reactAgent');
const { globalEventPublisher } = require('./event/reactEventPublisher');
const { llmConfig } = require('./config/llmConfig');

const app = express();
const PORT = process.env.PORT || 8080;

// 中间件
app.use(cors());
app.use(express.json());

// 创建 Agent 实例
const agent = new ReActAgent();

/**
 * 健康检查接口
 */
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    provider: llmConfig.provider,
    timestamp: new Date().toISOString()
  });
});

/**
 * 同步解决任务接口
 */
app.get('/react/solve', async (req, res) => {
  const { task } = req.query;
  
  if (!task) {
    return res.status(400).json({ error: '缺少 task 参数' });
  }

  try {
    const result = await agent.solve(task);
    res.json({ result });
  } catch (error) {
    console.error('Solve error:', error);
    res.status(500).json({ error: error.message });
  }
});

/**
 * 流式解决任务接口（SSE）
 */
app.get('/react/solve-stream', (req, res) => {
  const { task } = req.query;
  
  if (!task) {
    return res.status(400).json({ error: '缺少 task 参数' });
  }

  // 设置 SSE 响应头（包含 CORS）
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Credentials', 'true');

  // 事件监听器
  const eventListener = (event) => {
    try {
      res.write(`event: ${event.type}\n`);
      res.write(`data: ${JSON.stringify(event)}\n\n`);
    } catch (error) {
      console.error('SSE write error:', error);
    }
  };

  // 注册监听器
  globalEventPublisher.registerListener(eventListener);

  // 执行任务
  agent.solve(task)
    .then(result => {
      // 任务完成，清理监听器
      globalEventPublisher.unregisterListener(eventListener);
      res.end();
    })
    .catch(error => {
      console.error('Solve stream error:', error);
      globalEventPublisher.unregisterListener(eventListener);
      
      // 发送错误事件
      try {
        res.write(`event: error\n`);
        res.write(`data: ${JSON.stringify({ error: error.message })}\n\n`);
      } catch (e) {
        console.error('Error sending error event:', e);
      }
      
      res.end();
    });

  // 客户端断开连接时清理
  req.on('close', () => {
    globalEventPublisher.unregisterListener(eventListener);
  });
});

/**
 * 获取配置信息
 */
app.get('/config', (req, res) => {
  res.json({
    provider: llmConfig.provider,
    maxMessages: llmConfig.maxMessages,
    modelName: llmConfig.provider === 'qwen' 
      ? llmConfig.qwen.modelName 
      : llmConfig.openai.modelName
  });
});

/**
 * 获取记忆统计信息
 */
app.get('/memory/stats', (req, res) => {
  try {
    const stats = agent.getMemoryStats();
    res.json({
      success: true,
      stats
    });
  } catch (error) {
    console.error('Memory stats error:', error);
    res.status(500).json({ error: error.message });
  }
});

// 服务器实例
let server = null;

/**
 * 启动服务器（导出函数，供 Electron 主进程调用）
 */
function startServer(port = PORT) {
  return new Promise((resolve, reject) => {
    try {
      server = app.listen(port, () => {
        console.log(`
╔════════════════════════════════════════════════════════╗
║  Node.js ReAct MCP Backend                             ║
╠════════════════════════════════════════════════════════╣
║  服务器已启动                                           ║
║  端口: ${port}                                          ║
║  LLM 提供商: ${llmConfig.provider.toUpperCase()}                                    ║
║  模型: ${llmConfig.provider === 'qwen' ? llmConfig.qwen.modelName : llmConfig.openai.modelName}                                       ║
╠════════════════════════════════════════════════════════╣
║  接口:                                                  ║
║  - GET  /health           健康检查                      ║
║  - GET  /config           配置信息                      ║
║  - GET  /memory/stats     记忆统计                      ║
║  - GET  /react/solve      同步任务执行                  ║
║  - GET  /react/solve-stream  流式任务执行（SSE）        ║
╚════════════════════════════════════════════════════════╝
        `);
        resolve({ port, server });
      });
      
      server.on('error', (error) => {
        console.error('服务器启动失败:', error);
        reject(error);
      });
    } catch (error) {
      reject(error);
    }
  });
}

/**
 * 停止服务器
 */
function stopServer() {
  if (server) {
    server.close();
    server = null;
    console.log('服务器已停止');
  }
}

// 如果直接运行此文件，则启动服务器
if (require.main === module) {
  startServer();
}

module.exports = { startServer, stopServer };
