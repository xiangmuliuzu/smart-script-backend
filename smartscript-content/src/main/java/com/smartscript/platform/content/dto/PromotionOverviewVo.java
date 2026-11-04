package com.smartscript.platform.content.dto;

import java.math.BigDecimal;

/** 资源位概览（坑位总览顶部卡片） */
public class PromotionOverviewVo
{
    private String slotType;
    private int totalSlots;
    private int activeCount;
    private int lockedCount;
    private int idleCount;
    private int pendingCount;
    private BigDecimal monthRevenue;

    public String getSlotType() { return slotType; }
    public void setSlotType(String slotType) { this.slotType = slotType; }
    public int getTotalSlots() { return totalSlots; }
    public void setTotalSlots(int totalSlots) { this.totalSlots = totalSlots; }
    public int getActiveCount() { return activeCount; }
    public void setActiveCount(int activeCount) { this.activeCount = activeCount; }
    public int getLockedCount() { return lockedCount; }
    public void setLockedCount(int lockedCount) { this.lockedCount = lockedCount; }
    public int getIdleCount() { return idleCount; }
    public void setIdleCount(int idleCount) { this.idleCount = idleCount; }
    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    public BigDecimal getMonthRevenue() { return monthRevenue; }
    public void setMonthRevenue(BigDecimal monthRevenue) { this.monthRevenue = monthRevenue; }
}
