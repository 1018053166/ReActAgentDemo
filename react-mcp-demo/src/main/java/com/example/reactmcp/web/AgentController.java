package com.example.reactmcp.web;

import com.example.reactmcp.agent.McpAssistant;
import com.example.reactmcp.model.ReActStepEvent;
import com.example.reactmcp.service.ReActEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * ReAct Agent REST 接口
 * 提供智能问题解决能力
 */
@RestController
@RequestMapping("/react")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final McpAssistant assistant;
    private final ReActEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AgentController(McpAssistant assistant, ReActEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.assistant = assistant;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/solve")
    public String solve(@RequestParam String task) {
        log.info("");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("🚀 收到新的 ReAct 任务请求");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("📋 任务内容: {}", task);
        log.info("⏰ 开始时间: {}", java.time.LocalDateTime.now());
        log.info("───────────────────────────────────────────────────────────────");
        
        long startTime = System.currentTimeMillis();
        
        String result = assistant.solve(task);
        
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("✅ ReAct 任务执行完成");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("⏱️  总耗时: {}ms ({} 秒)", duration, String.format("%.2f", duration / 1000.0));
        log.info("📤 最终答案长度: {} 字符", result.length());
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("");
        
        return result;
    }
    
    @GetMapping(value = "/solve-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter solveStream(@RequestParam String task) {
        log.info("");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("🚀 收到新的 ReAct 流式任务请求");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("📋 任务内容: {}", task);
        log.info("⏰ 开始时间: {}", java.time.LocalDateTime.now());
        log.info("───────────────────────────────────────────────────────────────");
        
        SseEmitter emitter = new SseEmitter(300000L); // 5 分钟超时
        
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 注册事件监听器
                eventPublisher.registerListener(event -> {
                    try {
                        // 确保事件数据是字符串格式，而不是 LinkedHashMap
                        String eventData = objectMapper.writeValueAsString(event);
                        emitter.send(SseEmitter.event()
                                .name(event.getType())
                                .data(eventData));
                        log.debug("📤 发送事件: type={}", event.getType());
                    } catch (IOException e) {
                        log.error("发送 SSE 事件失败", e);
                        emitter.completeWithError(e);
                    }
                });
                
                // 执行 ReAct 任务
                String result = assistant.solve(task);
                
                // 发送最终答案
                emitter.send(SseEmitter.event()
                        .name("final_answer")
                        .data(objectMapper.writeValueAsString(ReActStepEvent.finalAnswer(result))));
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("✅ ReAct 流式任务执行完成, 耗时: {}ms", duration);
                
                emitter.complete();
            } catch (Exception e) {
                log.error("执行 ReAct 任务失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\": \"Processing error: " + e.getMessage() + "\"}"));
                } catch (IOException ioException) {
                    log.error("Error sending error event", ioException);
                }
                emitter.completeWithError(e);
            } finally {
                eventPublisher.clear();
            }
        });
        
        return emitter;
    }
}
