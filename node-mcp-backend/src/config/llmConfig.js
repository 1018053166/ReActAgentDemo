import dotenv from 'dotenv';

// 加载环境变量
dotenv.config();

/**
 * LLM 提供商配置
 */
export const llmConfig = {
  // 当前使用的提供商：qwen 或 openai
  provider: process.env.LLM_PROVIDER || 'qwen',
  
  // 最大消息历史数量
  maxMessages: parseInt(process.env.MAX_MESSAGES || '10'),
  
  // Qwen 配置
  qwen: {
    apiKey: process.env.QWEN_API_KEY || 'sk-your-qwen-api-key-here',
    modelName: process.env.QWEN_MODEL_NAME || 'qwen-turbo',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1'
  },
  
  // OpenAI 配置
  openai: {
    apiKey: process.env.OPENAI_API_KEY || 'sk-your-openai-key-here',
    modelName: process.env.OPENAI_MODEL_NAME || 'gpt-4o-mini',
    baseUrl: process.env.OPENAI_BASE_URL || 'https://api.openai.com/v1'
  }
};

/**
 * 获取当前 LLM 配置
 */
export function getCurrentLLMConfig() {
  const provider = llmConfig.provider.toLowerCase();
  
  if (provider === 'qwen') {
    return {
      provider: 'qwen',
      apiKey: llmConfig.qwen.apiKey,
      modelName: llmConfig.qwen.modelName,
      baseUrl: llmConfig.qwen.baseUrl
    };
  } else if (provider === 'openai') {
    return {
      provider: 'openai',
      apiKey: llmConfig.openai.apiKey,
      modelName: llmConfig.openai.modelName,
      baseUrl: llmConfig.openai.baseUrl
    };
  } else {
    throw new Error(`Unsupported LLM provider: ${provider}`);
  }
}
