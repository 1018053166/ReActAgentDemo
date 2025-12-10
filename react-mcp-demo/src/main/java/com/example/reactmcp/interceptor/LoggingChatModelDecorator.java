package com.example.reactmcp.interceptor;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ChatLanguageModel 日志装饰器
 * 用于记录大模型的完整交互过程，包括请求和响应
 */
public class LoggingChatModelDecorator implements ChatLanguageModel {

    private static final Logger log = LoggerFactory.getLogger(LoggingChatModelDecorator.class);
    private static int requestCounter = 0;
    
    private final ChatLanguageModel delegate;

    public LoggingChatModelDecorator(ChatLanguageModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return logAndGenerate(messages, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return logAndGenerate(messages, toolSpecifications);
    }

    private Response<AiMessage> logAndGenerate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        int currentRequest = ++requestCounter;
        
        log.info("╔═══════════════════════════════════════════════════════════════");
        log.info("║ 🤖 大模型调用 #{} - 开始", currentRequest);
        log.info("╠═══════════════════════════════════════════════════════════════");
        
        // 打印所有消息
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            log.info("║ 📨 消息 [{}] - 类型: {}", i + 1, msg.type());
            log.info("║    内容: {}", formatMessageContent(msg.text()));
        }
        
        // 打印工具规范
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            log.info("╠═══════════════════════════════════════════════════════════════");
            log.info("║ 🔧 可用工具: {} 个", toolSpecifications.size());
            toolSpecifications.forEach(tool -> {
                log.info("║    • {}: {}", tool.name(), tool.description());
            });
        }
        
        log.info("╠═══════════════════════════════════════════════════════════════");
        log.info("║ ⏳ 等待大模型响应...");
        
        long startTime = System.currentTimeMillis();
        Response<AiMessage> response;
        if (toolSpecifications != null) {
            response = delegate.generate(messages, toolSpecifications);
        } else {
            response = delegate.generate(messages);
        }
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("║ ✅ 响应耗时: {}ms", duration);
        log.info("╠═══════════════════════════════════════════════════════════════");
        log.info("║ 📥 大模型响应内容:");
        log.info("║    {}", formatMessageContent(response.content().text()));
        
        // 如果有工具调用，打印工具调用信息
        if (response.content().hasToolExecutionRequests()) {
            log.info("╠═══════════════════════════════════════════════════════════════");
            log.info("║ 🔧 工具调用请求:");
            response.content().toolExecutionRequests().forEach(request -> {
                log.info("║    • 工具名称: {}", request.name());
                log.info("║      参数: {}", request.arguments());
            });
        }
        
        log.info("╚═══════════════════════════════════════════════════════════════");
        
        return response;
    }

    private String formatMessageContent(String content) {
        if (content == null) {
            return "(空)";
        }
        // 对于过长的内容，进行格式化处理
        if (content.length() > 500) {
            return content.substring(0, 500) + "... (共 " + content.length() + " 字符)";
        }
        // 将多行内容缩进
        return content.replace("\n", "\n║    ");
    }
}
