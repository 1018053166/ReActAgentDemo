import axios from 'axios';
import { getCurrentLLMConfig } from '../config/llmConfig.js';
import { globalEventPublisher } from '../event/reactEventPublisher.js';
import { ReActStepEvent } from '../model/reactStepEvent.js';

/**
 * LLM 客户端 - 支持 OpenAI 和 Qwen
 */
export class LLMClient {
  constructor() {
    this.config = getCurrentLLMConfig();
    this.retryAttempts = 3;
    this.retryDelay = 2000; // 2秒
  }

  /**
   * 发送聊天请求
   */
  async chat(messages, tools = []) {
    const requestBody = {
      model: this.config.modelName,
      messages: messages,
      temperature: 0.7
    };

    // 如果有工具，添加到请求中
    if (tools && tools.length > 0) {
      requestBody.tools = tools;
      requestBody.tool_choice = 'auto';
    }

    return await this.sendWithRetry(requestBody);
  }

  /**
   * 带重试的请求发送
   */
  async sendWithRetry(requestBody, attempt = 1) {
    try {
      const response = await axios.post(
        `${this.config.baseUrl}/chat/completions`,
        requestBody,
        {
          headers: {
            'Authorization': `Bearer ${this.config.apiKey}`,
            'Content-Type': 'application/json'
          },
          timeout: 60000
        }
      );

      return response.data;
    } catch (error) {
      // 检查是否是限流错误
      if (this.isRateLimitError(error) && attempt < this.retryAttempts) {
        const delay = this.retryDelay * Math.pow(2, attempt - 1); // 指数退避
        console.log(`Rate limit hit, retrying in ${delay}ms (attempt ${attempt}/${this.retryAttempts})`);
        
        await this.sleep(delay);
        return await this.sendWithRetry(requestBody, attempt + 1);
      }

      // 检查是否是内容审查错误
      if (this.isContentFilterError(error)) {
        const errorMsg = '内容包含敏感词，请修改后重试';
        globalEventPublisher.publish(ReActStepEvent.error(errorMsg));
        throw new Error(errorMsg);
      }

      throw error;
    }
  }

  /**
   * 判断是否是限流错误
   */
  isRateLimitError(error) {
    if (!error.response) return false;
    const status = error.response.status;
    return status === 429 || status === 503;
  }

  /**
   * 判断是否是内容审查错误
   */
  isContentFilterError(error) {
    if (!error.response) return false;
    const data = error.response.data;
    
    // Qwen 的内容审查错误
    if (data && data.code === 'DataInspectionFailed') {
      return true;
    }
    
    // OpenAI 的内容审查错误
    if (data && data.error && data.error.code === 'content_filter') {
      return true;
    }
    
    return false;
  }

  /**
   * 睡眠函数
   */
  sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * 修正消息序列（确保 user/assistant 交替出现）
   */
  fixMessageSequence(messages) {
    if (!messages || messages.length === 0) {
      return messages;
    }

    const fixed = [];
    let lastRole = null;

    for (const msg of messages) {
      // 跳过 system 消息的检查
      if (msg.role === 'system') {
        fixed.push(msg);
        continue;
      }

      // 如果连续出现相同角色，合并消息
      if (lastRole === msg.role && fixed.length > 0) {
        const lastMsg = fixed[fixed.length - 1];
        if (lastMsg.role === msg.role) {
          lastMsg.content = lastMsg.content + '\n' + msg.content;
          continue;
        }
      }

      fixed.push(msg);
      lastRole = msg.role;
    }

    return fixed;
  }
}
