import { LLMClient } from '../llm/llmClient.js';
import { ToolRegistry } from '../tools/toolRegistry.js';
import { globalEventPublisher } from '../event/reactEventPublisher.js';
import { ReActStepEvent } from '../model/reactStepEvent.js';
import { llmConfig } from '../config/llmConfig.js';

/**
 * ReAct Agent - 推理与行动循环引擎
 */
export class ReActAgent {
  constructor() {
    this.llmClient = new LLMClient();
    this.toolRegistry = new ToolRegistry();
    this.maxIterations = 15; // 最大迭代次数
    this.systemPrompt = this.buildSystemPrompt();
  }

  /**
   * 解决用户任务
   */
  async solve(task) {
    const messages = [
      { role: 'system', content: this.systemPrompt },
      { role: 'user', content: task }
    ];

    let iteration = 0;
    let finalAnswer = null;

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
        }

        // 处理工具调用
        const toolCall = assistantMessage.tool_calls[0];
        const toolName = toolCall.function.name;
        const toolArgs = JSON.parse(toolCall.function.arguments);

        // 发布 Action 事件
        globalEventPublisher.publish(
          ReActStepEvent.action(iteration, `调用工具: ${toolName}(${JSON.stringify(toolArgs)})`)
        );

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

      return finalAnswer;
    } catch (error) {
      const errorMsg = `Agent 执行异常: ${error.message}`;
      globalEventPublisher.publish(ReActStepEvent.error(errorMsg));
      throw error;
    }
  }

  /**
   * 构建系统提示词
   */
  buildSystemPrompt() {
    return `你是一个基于 ReAct 框架的智能 Agent，必须严格遵循以下规则使用工具完成用户任务。

## 核心原则
- 你是工具驱动型 AI，不具备直接回答问题的能力，必须通过调用工具来完成所有任务
- 每次只能调用一个工具，等待观察结果后再决定下一步行动
- 你的目标是自主完成任务，最小化用户干预
- **关键：只调用完成任务所必需的工具，避免不必要的工具调用**

## 工具选择策略
1. **分析用户需求**：首先分析用户的具体需求，确定最少需要哪些工具来完成
2. **优先级排序**：根据任务类型选择最直接的工具，避免使用高级工具完成简单任务
3. **避免冗余**：不要为了"确保成功"而调用额外的验证工具，除非确实必要

## 工作流程（严格遵守）
1. Thought: 分析当前状态，明确下一步目标，思考最少需要哪些工具
2. Action: 调用最合适的工具（系统会执行）
3. Observation: 仔细分析工具返回的结果
4. 重复步骤 1-3，直到任务完成
5. Final Answer: 给出简洁明了的最终答案

## 可用工具分类

### 数学运算
- add(a, b): 加法
- subtract(a, b): 减法
- multiply(a, b): 乘法
- divide(a, b): 除法
- squareRoot(number): 平方根

### 文件系统操作
- readFile(filePath): 读取文件
- writeFile(filePath, content): 写入文件
- listDirectory(directoryPath): 列出目录
- deleteFile(filePath): 删除文件
- createDirectory(directoryPath): 创建目录

### 文档读取
- readWordDocument(filePath): 读取 Word 文档
- readExcelDocument(filePath, sheetName): 读取 Excel 文档

### 浏览器自动化
- navigate(url): 打开网页
- click(selector): 点击元素
- fill(selector, value): 填充表单
- screenshot(fileName): 截图
- getPageContent(): 获取页面内容
- getConsoleLogs(): 获取控制台日志

## 最佳实践
- 保持简洁：使用最少的工具完成任务
- 仔细观察：认真分析每个工具的返回结果
- 避免过度分析：不要为了"确保成功"而调用额外的验证工具
- 完成后给出明确的 Final Answer

## 示例

用户: 计算 12 + 8 的结果

Thought: 需要使用加法工具计算 12 + 8
Action: add(12, 8)
Observation: 计算结果: 12 + 8 = 20

Thought: 计算完成，得到结果
Final Answer: 12 + 8 = 20
`;
  }
}
