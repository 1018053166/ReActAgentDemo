package com.example.reactmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 通用配置类
 * 支持多种 LLM 提供商：Qwen、OpenAI 等
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j")
public class LangchainProperties {
    
    /**
     * 模型提供商：qwen | openai
     */
    private String provider = "qwen";
    
    /**
     * 消息窗口大小（上下文管理）
     */
    private Integer maxMessages = 10;
    
    /**
     * Qwen 模型配置
     */
    private QwenConfig qwen = new QwenConfig();
    
    /**
     * OpenAI 协议模型配置
     */
    private OpenAiConfig openai = new OpenAiConfig();

    // Getter & Setter
    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Integer getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(Integer maxMessages) {
        this.maxMessages = maxMessages;
    }

    public QwenConfig getQwen() {
        return qwen;
    }

    public void setQwen(QwenConfig qwen) {
        this.qwen = qwen;
    }

    public OpenAiConfig getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAiConfig openai) {
        this.openai = openai;
    }

    /**
     * Qwen 模型配置
     */
    public static class QwenConfig {
        private String apiKey;
        private String modelName = "qwen3-max";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }

    /**
     * OpenAI 协议模型配置
     */
    public static class OpenAiConfig {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey;
        private String modelName = "gpt-4o-mini";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }
}
