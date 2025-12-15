package com.example.reactmcp.config;

import com.example.reactmcp.agent.McpAssistant;
import com.example.reactmcp.interceptor.StreamingChatModelDecorator;
import com.example.reactmcp.service.ReActEventPublisher;
import com.example.reactmcp.tools.McpTools;
import com.example.reactmcp.tools.FileSystemTools;
import com.example.reactmcp.tools.DocumentReaderTools;
import com.example.reactmcp.tools.PlaywrightMcpTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 配置类
 * 支持多种 LLM 提供商：Qwen、OpenAI 等
 * 通过 langchain4j.provider 配置项切换
 */
@Configuration
public class LangchainConfig {

    private static final Logger log = LoggerFactory.getLogger(LangchainConfig.class);
    
    private final LangchainProperties properties;

    public LangchainConfig(LangchainProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建基础的 ChatLanguageModel
     * 根据 provider 配置自动选择 Qwen 或 OpenAI
     */
    @Bean
    public ChatLanguageModel baseChatLanguageModel() {
        String provider = properties.getProvider();
        log.info("Initializing ChatLanguageModel with provider: {}", provider);
        
        if ("openai".equalsIgnoreCase(provider)) {
            return createOpenAiChatModel();
        } else if ("qwen".equalsIgnoreCase(provider)) {
            return createQwenChatModel();
        } else {
            throw new IllegalArgumentException(
                "Unsupported LLM provider: " + provider + ". Supported values: qwen, openai"
            );
        }
    }

    /**
     * 创建 Qwen 模型
     */
    private ChatLanguageModel createQwenChatModel() {
        LangchainProperties.QwenConfig config = properties.getQwen();
        log.info("Creating QwenChatModel: model={}", config.getModelName());
        
        return QwenChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .build();
    }

    /**
     * 创建 OpenAI 协议模型（支持私有化部署）
     */
    private ChatLanguageModel createOpenAiChatModel() {
        LangchainProperties.OpenAiConfig config = properties.getOpenai();
        log.info("Creating OpenAiChatModel: baseUrl={}, model={}", 
                 config.getBaseUrl(), config.getModelName());
        
        return OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .build();
    }

    /**
     * 包装 ChatLanguageModel 为流式装饰器
     * 支持 ReAct 流式事件推送
     */
    @Bean
    public ChatLanguageModel chatLanguageModel(
            ChatLanguageModel baseChatLanguageModel, 
            ReActEventPublisher eventPublisher) {
        log.info("Wrapping ChatLanguageModel with StreamingChatModelDecorator");
        return new StreamingChatModelDecorator(baseChatLanguageModel, eventPublisher);
    }

    /**
     * 创建 ReAct Agent
     */
    @Bean
    public McpAssistant mcpAssistant(
            ChatLanguageModel chatLanguageModel, 
            McpTools mcpTools,
            FileSystemTools fileSystemTools,
            DocumentReaderTools documentReaderTools,
            PlaywrightMcpTools playwrightMcpTools) {
        log.info("Building McpAssistant with {} max messages", properties.getMaxMessages());
        return AiServices.builder(McpAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(properties.getMaxMessages()))
                .tools(mcpTools, fileSystemTools, documentReaderTools, playwrightMcpTools)
                .build();
    }
}
