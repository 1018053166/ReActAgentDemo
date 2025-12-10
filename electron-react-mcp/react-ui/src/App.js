import React, { useState, useEffect } from 'react';
import './App.css';

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

    addLog(`执行任务: ${taskInput}`, 'info');
    
    try {
      // 这里可以添加与后端通信的逻辑
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
