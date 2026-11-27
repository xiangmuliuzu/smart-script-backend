package com.smartscript.platform.trade.agent.domain;

import java.time.LocalDateTime;

public class TradeAgentSession
{
    private Long sessionId;
    private Long userId;
    private String title;
    private String briefJson;
    private int totalTokens;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBriefJson() { return briefJson; }
    public void setBriefJson(String briefJson) { this.briefJson = briefJson; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
