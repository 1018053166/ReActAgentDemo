package com.example.reactmcp.service;

import com.example.reactmcp.model.ReActStepEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ReAct 事件发布器
 * 用于在 ReAct 执行过程中发布事件
 */
@Component
public class ReActEventPublisher {
    
    private final ThreadLocal<List<Consumer<ReActStepEvent>>> listeners = ThreadLocal.withInitial(ArrayList::new);
    
    /**
     * 注册事件监听器
     */
    public void registerListener(Consumer<ReActStepEvent> listener) {
        listeners.get().add(listener);
    }
    
    /**
     * 发布事件
     */
    public void publish(ReActStepEvent event) {
        List<Consumer<ReActStepEvent>> currentListeners = listeners.get();
        for (Consumer<ReActStepEvent> listener : currentListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // 忽略监听器异常，避免影响主流程
            }
        }
    }
    
    /**
     * 清理当前线程的监听器
     */
    public void clear() {
        listeners.get().clear();
        listeners.remove();
    }
}
