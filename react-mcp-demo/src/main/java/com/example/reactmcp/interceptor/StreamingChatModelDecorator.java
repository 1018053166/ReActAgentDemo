package com.example.reactmcp.interceptor;

import com.example.reactmcp.model.ReActStepEvent;
import com.example.reactmcp.service.ReActEventPublisher;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 支持流式事件发布的 ChatLanguageModel 装饰器
 */
public class StreamingChatModelDecorator implements ChatLanguageModel {
    
    private static final Logger log = LoggerFactory.getLogger(StreamingChatModelDecorator.class);
    
    private final ChatLanguageModel delegate;
    private final ReActEventPublisher eventPublisher;
    
    public StreamingChatModelDecorator(ChatLanguageModel delegate, ReActEventPublisher eventPublisher) {
        this.delegate = delegate;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        log.debug("Generating response for {} messages", messages.size());
        
        // 验证和修复消息序列
        List<ChatMessage> fixedMessages = fixMessageSequence(messages);
        
        // 实现重试逻辑，处理 API 限流问题
        int maxRetries = 3;
        int retryDelay = 2000; // 2秒初始延迟
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Response<AiMessage> response = delegate.generate(fixedMessages);
                
                // 发布 Thought 事件（如果有文本内容）
                if (response.content() != null && response.content().text() != null) {
                    String text = response.content().text();
                    if (!text.isEmpty()) {
                        eventPublisher.publish(ReActStepEvent.thought(text));
                    }
                }
                
                // 发布 Action 事件（如果有工具调用）
                if (response.content() != null && response.content().hasToolExecutionRequests()) {
                    for (ToolExecutionRequest request : response.content().toolExecutionRequests()) {
                        eventPublisher.publish(ReActStepEvent.action(
                            request.name(),
                            request.arguments()
                        ));
                    }
                }
                
                return response;
            } catch (Exception e) {
                // 检查是否是内容审查异常
                if (isContentInspectionError(e)) {
                    log.warn("检测到敏感内容，已被阿里云内容审查拦截: {}", e.getMessage());
                    String warningMessage = "⚠️ 请求包含敏感内容，已被系统拦截。请调整输入内容后重试。";
                    eventPublisher.publish(ReActStepEvent.thought(warningMessage));
                    // 返回一个安全的响应
                    return Response.from(AiMessage.from(warningMessage));
                }
                
                // 检查是否是限流错误
                if (isRateLimitError(e) && attempt < maxRetries) {
                    log.warn("API 限流错误，第 {} 次尝试失败，{} 秒后重试: {}", attempt, retryDelay / 1000, e.getMessage());
                    try {
                        TimeUnit.MILLISECONDS.sleep(retryDelay);
                        retryDelay *= 2; // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                } else {
                    // 非限流错误或已达到最大重试次数
                    throw new RuntimeException("生成 AI 响应时发生错误: " + e.getMessage(), e);
                }
            }
        }
        
        // 理论上不会到达这里，但为了安全起见
        throw new RuntimeException("达到最大重试次数，仍然无法获取响应");
    }
    
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        log.debug("Generating response with {} tools", toolSpecifications != null ? toolSpecifications.size() : 0);
        
        // 验证和修复消息序列
        List<ChatMessage> fixedMessages = fixMessageSequence(messages);
        
        // 实现重试逻辑，处理 API 限流问题
        int maxRetries = 3;
        int retryDelay = 2000; // 2秒初始延迟
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Response<AiMessage> response = delegate.generate(fixedMessages, toolSpecifications);
                
                // 发布 Thought 事件
                if (response.content() != null && response.content().text() != null) {
                    String text = response.content().text();
                    if (!text.isEmpty()) {
                        eventPublisher.publish(ReActStepEvent.thought(text));
                    }
                }
                
                // 发布 Action 事件
                if (response.content() != null && response.content().hasToolExecutionRequests()) {
                    for (ToolExecutionRequest request : response.content().toolExecutionRequests()) {
                        eventPublisher.publish(ReActStepEvent.action(
                            request.name(),
                            request.arguments()
                        ));
                    }
                }
                
                return response;
            } catch (Exception e) {
                // 检查是否是内容审查异常
                if (isContentInspectionError(e)) {
                    log.warn("检测到敏感内容，已被阿里云内容审查拦截: {}", e.getMessage());
                    String warningMessage = "⚠️ 请求包含敏感内容，已被系统拦截。请调整输入内容后重试。";
                    eventPublisher.publish(ReActStepEvent.thought(warningMessage));
                    // 返回一个安全的响应
                    return Response.from(AiMessage.from(warningMessage));
                }
                
                // 检查是否是限流错误
                if (isRateLimitError(e) && attempt < maxRetries) {
                    log.warn("API 限流错误，第 {} 次尝试失败，{} 秒后重试: {}", attempt, retryDelay / 1000, e.getMessage());
                    try {
                        TimeUnit.MILLISECONDS.sleep(retryDelay);
                        retryDelay *= 2; // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                } else {
                    // 非限流错误或已达到最大重试次数
                    throw new RuntimeException("生成 AI 响应时发生错误: " + e.getMessage(), e);
                }
            }
        }
        
        // 理论上不会到达这里，但为了安全起见
        throw new RuntimeException("达到最大重试次数，仍然无法获取响应");
    }
    
    /**
     * 检查异常是否为内容审查错误
     */
    private boolean isContentInspectionError(Exception e) {
        // 检查异常消息中是否包含内容审查相关的关键词
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        // 阿里云 DashScope 的内容审查错误标识
        return message.contains("DataInspectionFailed") || 
               message.contains("Input data may contain inappropriate content") ||
               message.contains("内容审查") ||
               message.contains("敏感内容");
    }
    
    /**
     * 检查异常是否为 API 限流错误
     */
    private boolean isRateLimitError(Exception e) {
        // 检查异常消息中是否包含限流相关的关键词
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        // 阿里云 DashScope 的限流错误标识
        return message.contains("Request rate increased too quickly") || 
               message.contains("Throttling") ||
               message.contains("Rate limit") ||
               message.contains("429") ||
               message.contains("Too many requests");
    }
    
    /**
     * 验证和修复消息序列，确保符合 Qwen API 要求
     * 1. 第一条非系统消息必须是 UserMessage
     * 2. 最后一条消息必须是 UserMessage 或 ToolExecutionResultMessage
     * 3. 移除无效的空消息
     */
    private List<ChatMessage> fixMessageSequence(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        
        List<ChatMessage> fixed = new ArrayList<>(messages);
        
        // 移除空消息
        Iterator<ChatMessage> iterator = fixed.iterator();
        while (iterator.hasNext()) {
            ChatMessage msg = iterator.next();
            if (msg instanceof UserMessage) {
                UserMessage userMsg = (UserMessage) msg;
                if (userMsg.singleText().isEmpty()) {
                    iterator.remove();
                }
            } else if (msg instanceof AiMessage) {
                AiMessage aiMsg = (AiMessage) msg;
                if ((aiMsg.text() == null || aiMsg.text().isEmpty()) && 
                    !aiMsg.hasToolExecutionRequests()) {
                    iterator.remove();
                }
            }
        }
        
        // 确保第一条非系统消息是 UserMessage
        boolean foundFirstNonSystem = false;
        for (int i = 0; i < fixed.size(); i++) {
            ChatMessage msg = fixed.get(i);
            if (!(msg instanceof SystemMessage)) {
                if (!foundFirstNonSystem) {
                    foundFirstNonSystem = true;
                    if (!(msg instanceof UserMessage)) {
                        // 插入一个虚拟的用户消息
                        fixed.add(i, UserMessage.from("继续执行任务"));
                        break;
                    }
                }
            }
        }
        
        // 确保最后一条消息有效
        if (!fixed.isEmpty()) {
            ChatMessage lastMsg = fixed.get(fixed.size() - 1);
            if (lastMsg instanceof SystemMessage || 
                (lastMsg instanceof AiMessage && !((AiMessage) lastMsg).hasToolExecutionRequests())) {
                // 添加一个虚拟的用户消息
                fixed.add(UserMessage.from("继续执行任务"));
            }
        }
        
        log.debug("Fixed message sequence: {} -> {} messages", messages.size(), fixed.size());
        return fixed;
    }
    
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, List.of(toolSpecification));
    }
}