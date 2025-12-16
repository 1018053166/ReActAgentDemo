import { LLMClient } from '../llm/llmClient.js';
import { ToolRegistry } from '../tools/toolRegistry.js';
import { globalEventPublisher } from '../event/reactEventPublisher.js';
import { ReActStepEvent } from '../model/reactStepEvent.js';
import { llmConfig } from '../config/llmConfig.js';
import { TaskMemory } from '../memory/taskMemory.js';

/**
 * ReAct Agent - 推理与行动循环引擎
 */
export class ReActAgent {
  constructor() {
    this.llmClient = new LLMClient();
    this.toolRegistry = new ToolRegistry();
    this.taskMemory = new TaskMemory();
    this.maxIterations = 15; // 最大迭代次数
    this.systemPrompt = this.buildSystemPrompt();
    
    // 初始化记忆系统
    this.taskMemory.initialize();
  }

  /**
   * 解决用户任务
   */
  async solve(task) {
    // 搜索相似任务记忆
    const similarTasks = this.taskMemory.searchSimilarTasks(task, 2);
    
    // 构建包含记忆引用的系统提示词
    let systemPromptWithMemory = this.systemPrompt;
    if (similarTasks.length > 0) {
      const memoryContext = this.taskMemory.formatMemoriesForPrompt(similarTasks);
      systemPromptWithMemory = this.systemPrompt + memoryContext;
      console.log(`[ReActAgent] 找到 ${similarTasks.length} 条相似任务记忆`);
    }
    
    const messages = [
      { role: 'system', content: systemPromptWithMemory },
      { role: 'user', content: task }
    ];

    let iteration = 0;
    let finalAnswer = null;
    const executionSteps = []; // 记录执行步骤

    try {
      while (iteration < this.maxIterations && !finalAnswer) {
        iteration++;
        
        // 发布 Thought 事件（准备思考）
        globalEventPublisher.publish(
          ReActStepEvent.thought(iteration, `正在分析任务... (第 ${iteration} 轮)`)
        );

        // 调用 LLM
        const tools = this.toolRegistry.getAllToolDefinitions();
        const fixedMessages = this.llmClient.fixMessageSequence(messages);
        const response = await this.llmClient.chat(fixedMessages, tools);

        const choice = response.choices[0];
        const assistantMessage = choice.message;

        // 如果没有工具调用，说明可能是 Final Answer
        if (!assistantMessage.tool_calls || assistantMessage.tool_calls.length === 0) {
          finalAnswer = assistantMessage.content || '任务已完成';
          
          // 发布最终答案事件
          globalEventPublisher.publish(ReActStepEvent.finalAnswer(finalAnswer));
          break;
        }

        // 提取 Thought（从 assistant message content）
        if (assistantMessage.content) {
          globalEventPublisher.publish(
            ReActStepEvent.thought(iteration, assistantMessage.content)
          );
          executionSteps.push({
            type: 'thought',
            iteration,
            content: assistantMessage.content
          });
        }

        // 处理工具调用
        const toolCall = assistantMessage.tool_calls[0];
        const toolName = toolCall.function.name;
        const toolArgs = JSON.parse(toolCall.function.arguments);

        // 发布 Action 事件（格式化工具调用信息）
        const toolCallStr = `${toolName}(${Object.entries(toolArgs).map(([k, v]) => `${k}: ${JSON.stringify(v)}`).join(', ')})`;
        globalEventPublisher.publish(
          ReActStepEvent.action(iteration, toolCallStr)
        );
        executionSteps.push({
          type: 'action',
          iteration,
          content: toolCallStr
        });

        // 执行工具
        let observation = '';
        try {
          observation = await this.toolRegistry.executeTool(toolName, toolArgs);
        } catch (error) {
          observation = `工具执行失败: ${error.message}`;
        }

        // 发布 Observation 事件
        globalEventPublisher.publish(
          ReActStepEvent.observation(iteration, observation)
        );
        executionSteps.push({
          type: 'observation',
          iteration,
          content: observation
        });

        // 将助手消息和工具结果添加到对话历史
        messages.push(assistantMessage);
        messages.push({
          role: 'tool',
          tool_call_id: toolCall.id,
          content: observation
        });

        // 限制消息历史长度
        if (messages.length > llmConfig.maxMessages) {
          const systemMsg = messages[0];
          const recentMessages = messages.slice(-(llmConfig.maxMessages - 1));
          messages.splice(0, messages.length, systemMsg, ...recentMessages);
        }
      }

      // 如果达到最大迭代次数仍未完成
      if (!finalAnswer) {
        finalAnswer = `已达到最大迭代次数 (${this.maxIterations})，任务可能未完全完成。`;
        globalEventPublisher.publish(ReActStepEvent.finalAnswer(finalAnswer));
      }

      // 保存任务记忆（只保存复杂任务，步骤 >= 3）
      if (executionSteps.length >= 3) {
        await this.taskMemory.saveTask(
          task,
          executionSteps,
          finalAnswer,
          !finalAnswer.includes('未完全完成')
        );
      }

      return finalAnswer;
    } catch (error) {
      const errorMsg = `Agent 执行异常: ${error.message}`;
      globalEventPublisher.publish(ReActStepEvent.error(errorMsg));
      
      // 保存失败记忆
      if (executionSteps.length > 0) {
        await this.taskMemory.saveTask(task, executionSteps, errorMsg, false);
      }
      
      throw error;
    }
  }

  /**
   * 构建系统提示词
   */
  buildSystemPrompt() {
    const tools = this.toolRegistry.getAllToolDefinitions();
    const toolsDescription = this.formatToolsDescription(tools);
    
    return `你是一个基于 ReAct 框架的智能 Agent，必须严格遵循以下规则使用工具完成用户任务。

## 核心原则
- 你是工具驱动型 AI，不具备直接回答问题的能力，必须通过调用工具来完成所有任务
- 每次只能调用一个工具，等待观察结果后再决定下一步行动
- 你的目标是自主完成任务，最小化用户干预
- **关键：只调用完成任务所必需的工具，避免不必要的工具调用**

## 工具选择策略
1. **分析用户需求**：首先分析用户的具体需求，确定最少需要哪些工具来完成
2. **优先级排序**：根据任务类型选择最直接的工具，避免使用高级工具完成简单任务
3. **避免冗余**：不要为了“确保成功”而调用额外的验证工具，除非确实必要
4. **利用历史经验**：如果有相似任务的历史成功案例，参考其工具使用策略

## 工作流程（严格遵守）
1. **Thought**: 分析当前状态，明确下一步目标，思考最少需要哪些工具
2. **Action**: 调用最合适的工具（系统会执行）
3. **Observation**: 仔细分析工具返回的结果
4. 重复步骤 1-3，直到任务完成
5. **Final Answer**: 给出简洁明了的最终答案

## 可用工具

${toolsDescription}

## 增强能力

### 浏览器自动化最佳实践
- **等待页面加载**: 在进行 fill 或 click 之前，先使用 waitForSelector 确保元素已出现
- **元素选择器**: 优先使用 id、name 属性，其次是 class，最后才是复杂的 CSS 选择器
- **表单提交**: 填充完表单后，可以使用 press('Enter') 或 click 提交按钮
- **页面导航**: 对于搜索类任务，直接使用带参数的 URL（如 https://example.com/search?q=keyword）比填充表单更高效

### 文件操作注意事项
- **路径处理**: 始终使用绝对路径或确保相对路径正确
- **文件存在性**: 写入文件前不需要验证文件是否存在，直接创建即可
- **目录创建**: 如果需要在不存在的目录中创建文件，先使用 createDirectory

### 复杂任务分解
- **任务拆分**: 将复杂任务拆分为多个子目标，逐个完成
- **中间验证**: 对关键步骤进行验证，确保每个子目标都达成
- **错误处理**: 如果工具执行失败，分析错误原因并尝试替代方案

### 智能脚本生成（重要）
**何时生成脚本**：
- 需要多步骤命令组合时（如批量处理文件）
- 需要条件判断或循环时
- 需要复杂数据处理时

**何时直接执行命令**：
- 单一简单命令（如 ls, pwd, date）
- 查询类操作（如 ps, curl）

**脚本语言选择**：
- **shell/bash**: 系统操作、文件处理
- **python**: 数据处理、复杂逻辑
- **node**: JSON处理、API调用

## 最佳实践
- **保持简洁**: 使用最少的工具完成任务
- **仔细观察**: 认真分析每个工具的返回结果
- **避免过度分析**: 不要为了“确保成功”而调用额外的验证工具
- **明确结束**: 完成后给出明确的 Final Answer

## 示例

### 示例1：简单计算
用户: 计算 12 + 8 的结果

Thought: 需要使用加法工具计算 12 + 8
Action: add(a: 12, b: 8)
Observation: 计算结果: 12 + 8 = 20

Thought: 计算完成，得到结果
Final Answer: 12 + 8 = 20

### 示例2：浏览器搜索
用户: 在百度搜索 Python 教程

Thought: 需要打开百度搜索页面，直接使用搜索 URL 更高效
Action: navigate(url: "https://www.baidu.com/s?wd=Python%E6%95%99%E7%A8%8B")
Observation: 成功导航到: https://www.baidu.com/s?wd=Python%E6%95%99%E7%A8%8B

Thought: 已成功打开搜索结果页面
Final Answer: 已在百度搜索 "Python教程"，结果页面已打开。
`;
  }

  /**
   * 格式化工具描述
   */
  formatToolsDescription(tools) {
    // 按类别分组
    const categories = {
      '数学运算': [],
      '文件系统操作': [],
      '文档读取': [],
      '浏览器自动化': []
    };

    tools.forEach(tool => {
      const name = tool.function.name;
      const desc = tool.function.description;
      const params = tool.function.parameters?.properties || {};
      const required = tool.function.parameters?.required || [];
      
      // 格式化参数
      const paramStr = Object.entries(params)
        .map(([key, info]) => {
          const req = required.includes(key) ? '*' : '';
          return `${key}${req}`;
        })
        .join(', ');
      
      const toolDesc = `- **${name}**(${paramStr}): ${desc}`;
      
      // 分类
      if (['add', 'subtract', 'multiply', 'divide', 'squareRoot'].includes(name)) {
        categories['数学运算'].push(toolDesc);
      } else if (['readFile', 'writeFile', 'listDirectory', 'deleteFile', 'createDirectory'].includes(name)) {
        categories['文件系统操作'].push(toolDesc);
      } else if (['readWordDocument', 'readExcelDocument'].includes(name)) {
        categories['文档读取'].push(toolDesc);
      } else {
        categories['浏览器自动化'].push(toolDesc);
      }
    });

    // 生成描述
    let description = '';
    Object.entries(categories).forEach(([category, items]) => {
      if (items.length > 0) {
        description += '### ' + category + '\n' + items.join('\n') + '\n\n';
      }
    });

    return description;
  }

  /**
   * 获取记忆统计信息
   */
  getMemoryStats() {
    return this.taskMemory.getStats();
  }
}
