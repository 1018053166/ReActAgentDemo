import fs from 'fs/promises';
import path from 'path';

/**
 * 任务记忆管理器
 * 存储历史执行过的复杂任务及其解决方案
 */
export class TaskMemory {
  constructor() {
    this.memoryFile = path.join(process.cwd(), 'data', 'task-memory.json');
    this.memories = [];
    this.maxMemories = 100; // 最多保存100条记忆
  }

  /**
   * 初始化记忆系统
   */
  async initialize() {
    try {
      // 确保数据目录存在
      await fs.mkdir(path.dirname(this.memoryFile), { recursive: true });
      
      // 加载已有记忆
      try {
        const data = await fs.readFile(this.memoryFile, 'utf-8');
        this.memories = JSON.parse(data);
        console.log(`[TaskMemory] 加载了 ${this.memories.length} 条历史记忆`);
      } catch (error) {
        // 文件不存在或格式错误，初始化为空数组
        this.memories = [];
        console.log('[TaskMemory] 初始化新的记忆库');
      }
    } catch (error) {
      console.error('[TaskMemory] 初始化失败:', error.message);
    }
  }

  /**
   * 保存任务执行记录
   */
  async saveTask(task, steps, result, success = true) {
    const memory = {
      id: Date.now().toString(),
      timestamp: new Date().toISOString(),
      task: task,
      steps: steps,
      result: result,
      success: success,
      toolsUsed: this.extractToolsUsed(steps),
      complexity: steps.length // 用步骤数量衡量复杂度
    };

    this.memories.unshift(memory); // 新记忆放在最前面

    // 限制记忆数量
    if (this.memories.length > this.maxMemories) {
      this.memories = this.memories.slice(0, this.maxMemories);
    }

    // 持久化到文件
    try {
      await fs.writeFile(
        this.memoryFile,
        JSON.stringify(this.memories, null, 2),
        'utf-8'
      );
      console.log(`[TaskMemory] 已保存任务记忆: ${task.substring(0, 50)}...`);
    } catch (error) {
      console.error('[TaskMemory] 保存记忆失败:', error.message);
    }
  }

  /**
   * 搜索相似任务
   */
  searchSimilarTasks(currentTask, limit = 3) {
    if (!currentTask || this.memories.length === 0) {
      return [];
    }

    const currentWords = this.tokenize(currentTask);
    
    // 计算相似度并排序
    const scored = this.memories
      .filter(m => m.success) // 只返回成功的案例
      .map(memory => ({
        memory,
        score: this.calculateSimilarity(currentWords, this.tokenize(memory.task))
      }))
      .filter(item => item.score > 0.3) // 相似度阈值
      .sort((a, b) => b.score - a.score)
      .slice(0, limit);

    return scored.map(item => item.memory);
  }

  /**
   * 获取复杂任务案例
   */
  getComplexTasks(minSteps = 5, limit = 5) {
    return this.memories
      .filter(m => m.success && m.complexity >= minSteps)
      .slice(0, limit);
  }

  /**
   * 提取使用的工具列表
   */
  extractToolsUsed(steps) {
    const tools = new Set();
    steps.forEach(step => {
      if (step.type === 'action' && step.content) {
        // 从 "toolName(args)" 中提取 toolName
        const match = step.content.match(/^(\w+)\(/);
        if (match) {
          tools.add(match[1]);
        }
      }
    });
    return Array.from(tools);
  }

  /**
   * 分词（简单实现）
   */
  tokenize(text) {
    return text
      .toLowerCase()
      .replace(/[^\w\s\u4e00-\u9fa5]/g, ' ')
      .split(/\s+/)
      .filter(word => word.length > 1);
  }

  /**
   * 计算文本相似度（Jaccard 相似度）
   */
  calculateSimilarity(words1, words2) {
    const set1 = new Set(words1);
    const set2 = new Set(words2);
    
    const intersection = new Set([...set1].filter(x => set2.has(x)));
    const union = new Set([...set1, ...set2]);
    
    return union.size > 0 ? intersection.size / union.size : 0;
  }

  /**
   * 格式化记忆为提示词引用
   */
  formatMemoriesForPrompt(memories) {
    if (!memories || memories.length === 0) {
      return '';
    }

    const examples = memories.map((mem, idx) => {
      const stepsStr = mem.steps
        .filter(s => s.type === 'action' || s.type === 'observation')
        .map(s => `  ${s.type === 'action' ? 'Action' : 'Observation'}: ${s.content}`)
        .join('\n');

      return `
### 案例 ${idx + 1}: ${mem.task}
${stepsStr}
Result: ${mem.result}
Tools Used: ${mem.toolsUsed.join(', ')}
`;
    });

    return `
## 历史成功案例（供参考）

以下是类似任务的历史执行记录，可以参考其工具选择和执行流程：
${examples.join('\n')}
`;
  }

  /**
   * 获取统计信息
   */
  getStats() {
    const totalTasks = this.memories.length;
    const successTasks = this.memories.filter(m => m.success).length;
    const avgSteps = this.memories.reduce((sum, m) => sum + m.complexity, 0) / (totalTasks || 1);
    
    const toolUsage = {};
    this.memories.forEach(m => {
      m.toolsUsed.forEach(tool => {
        toolUsage[tool] = (toolUsage[tool] || 0) + 1;
      });
    });

    return {
      totalTasks,
      successTasks,
      successRate: totalTasks > 0 ? (successTasks / totalTasks * 100).toFixed(1) : 0,
      avgSteps: avgSteps.toFixed(1),
      mostUsedTools: Object.entries(toolUsage)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5)
        .map(([tool, count]) => ({ tool, count }))
    };
  }
}
