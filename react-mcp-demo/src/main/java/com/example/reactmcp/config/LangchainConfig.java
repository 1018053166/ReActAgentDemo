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
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 配置类
 * 配置 Qwen 模型和 ReAct Agent
 */
@Configuration
public class LangchainConfig {

    private static final Logger log = LoggerFactory.getLogger(LangchainConfig.class);
    
    private final LangchainProperties properties;

    public LangchainConfig(LangchainProperties properties) {
        this.properties = properties;
    }

    @Bean
    public QwenChatModel qwenChatModel() {
        log.info("Initializing Qwen Chat Model: {}", properties.getModelName());
        return QwenChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .build();
    }

    @Bean
    public ChatLanguageModel chatLanguageModel(QwenChatModel qwenChatModel, ReActEventPublisher eventPublisher) {
        log.info("Wrapping QwenChatModel with StreamingChatModelDecorator");
        return new StreamingChatModelDecorator(qwenChatModel, eventPublisher);
    }

    @Bean
    public McpAssistant mcpAssistant(ChatLanguageModel chatLanguageModel, 
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
