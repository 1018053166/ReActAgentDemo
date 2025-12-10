package com.example.reactmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ReAct 执行步骤事件
 * 用于流式输出每一轮的推理和执行过程
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReActStepEvent {
    
    private String type; // "thought", "action", "observation", "final_answer"
    private String content;
    private String toolName;
    private String toolInput;
    private String toolOutput;
    private Long timestamp;
    
    public ReActStepEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public static ReActStepEvent thought(String content) {
        ReActStepEvent event = new ReActStepEvent();
        event.setType("thought");
        event.setContent(content);
        return event;
    }
    
    public static ReActStepEvent action(String toolName, String toolInput) {
        ReActStepEvent event = new ReActStepEvent();
        event.setType("action");
        event.setToolName(toolName);
        event.setToolInput(toolInput);
        return event;
    }
    
    public static ReActStepEvent observation(String toolOutput) {
        ReActStepEvent event = new ReActStepEvent();
        event.setType("observation");
        event.setToolOutput(toolOutput);
        return event;
    }
    
    public static ReActStepEvent finalAnswer(String content) {
        ReActStepEvent event = new ReActStepEvent();
        event.setType("final_answer");
        event.setContent(content);
        return event;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolInput() {
        return toolInput;
    }

    public void setToolInput(String toolInput) {
        this.toolInput = toolInput;
    }

    public String getToolOutput() {
        return toolOutput;
    }

    public void setToolOutput(String toolOutput) {
        this.toolOutput = toolOutput;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
