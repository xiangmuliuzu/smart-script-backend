package com.smartscript.platform.trade.agent.domain;

import java.time.LocalDateTime;

public class TradeAgentMessage
{
    private Long messageId;
    private Long sessionId;
    private String role;
    private String content;
    private String toolCallsJson;
    private String toolCallId;
    private String toolName;
    private String structuredPayloadJson;
    private int tokens;
    private LocalDateTime createdAt;

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getToolCallsJson() { return toolCallsJson; }
    public void setToolCallsJson(String toolCallsJson) { this.toolCallsJson = toolCallsJson; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getStructuredPayloadJson() { return structuredPayloadJson; }
    public void setStructuredPayloadJson(String structuredPayloadJson) { this.structuredPayloadJson = structuredPayloadJson; }

    public int getTokens() { return tokens; }
    public void setTokens(int tokens) { this.tokens = tokens; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
