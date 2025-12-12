import React, { useState, useEffect } from 'react';
import './App.css';

const API_BASE_URL = 'http://localhost:8080';

function App() {
  const [logs, setLogs] = useState([]);
  const [taskInput, setTaskInput] = useState('');
  const [isConnected, setIsConnected] = useState(false);
  const [springBootStatus, setSpringBootStatus] = useState({ running: false, port: null });

  useEffect(() => {
    // 初始化应用
    const init = async () => {
      try {
        // 检查Spring Boot服务状态
        const status = await window.electronAPI.invoke('spring-boot-status');
        setSpringBootStatus(status);
        setIsConnected(status.running);
        
        if (status.running) {
          addLog(`服务已连接 (端口: ${status.port})`, 'success');
        } else {
          addLog('服务未运行', 'error');
        }
        addLog('应用已初始化', 'info');
      } catch (error) {
        addLog(`初始化失败: ${error.message}`, 'error');
      }
    };

    init();
  }, []);

  const addLog = (message, type = 'info') => {
    const timestamp = new Date().toLocaleTimeString();
    setLogs(prevLogs => [...prevLogs, { timestamp, message, type }]);
  };

  const handleExecuteTask = async () => {
    if (!taskInput.trim()) {
      addLog('请输入任务', 'warning');
      return;
    }

    if (!isConnected) {
      addLog('错误: 无法连接到后端服务，请检查服务是否正常运行', 'error');
      return;
    }

    addLog(`执行任务: ${taskInput}`, 'info');
    
    try {
      // 调用后端 ReAct 流式接口
      const response = await fetch(`${API_BASE_URL}/react/solve-stream?task=${encodeURIComponent(taskInput)}`);
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            try {
              const data = JSON.parse(line.substring(5).trim());
              if (data.type === 'thought') {
                addLog(`💭 思考: ${data.content}`, 'info');
              } else if (data.type === 'action') {
                addLog(`🔧 执行: ${data.content}`, 'info');
              } else if (data.type === 'observation') {
                addLog(`👁️ 观察: ${data.content}`, 'info');
              } else if (data.type === 'final_answer') {
                addLog(`✅ 答案: ${data.content}`, 'success');
              }
            } catch (e) {
              // 忽略 JSON 解析错误
            }
          }
        }
      }

      addLog('任务执行完成', 'success');
    } catch (error) {
      addLog(`任务执行失败: ${error.message}`, 'error');
    }
    
    setTaskInput('');
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>ReAct MCP 客户端</h1>
        <div className="status-bar">
          <span className={`status ${isConnected ? 'connected' : 'disconnected'}`}>
            {isConnected ? '已连接' : '未连接'}
          </span>
          {springBootStatus.running && (
            <span className="port-info">
              Spring Boot 端口: {springBootStatus.port}
            </span>
          )}
        </div>
      </header>
      
      <main className="App-main">
        <div className="task-input-container">
          <input
            type="text"
            value={taskInput}
            onChange={(e) => setTaskInput(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleExecuteTask()}
            placeholder="输入任务..."
            className="task-input"
          />
          <button onClick={handleExecuteTask} className="execute-button">
            执行
          </button>
        </div>
        
        <div className="logs-container">
          <h2>执行日志</h2>
          <div className="logs">
            {logs.map((log, index) => (
              <div key={index} className={`log-entry ${log.type}`}>
                <span className="timestamp">[{log.timestamp}]</span>
                <span className="message">{log.message}</span>
              </div>
            ))}
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;
