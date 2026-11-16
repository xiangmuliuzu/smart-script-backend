package com.smartscript.platform.trade.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * C module: demand stage extend history entity (sys_demand_extend_apply).
 * One row per extend action: pending -> approved/rejected, never overwritten;
 * a new application after a decided one inserts a fresh row.
 * apply_type distinguishes creator-requested extension from publisher self extension.
 */
public class SysDemandExtendApply
{
    public static final String STATUS_PENDING  = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    /** 创作者申请延时（需发布者处理） */
    public static final String TYPE_REQUEST = "request";
    /** 发布者自主延时（不经申请，写入即生效） */
    public static final String TYPE_SELF    = "self";

    private Long applyId;
    private Long demandId;
    private Long stageId;
    /** 所属轮次序号（联查带出，仅历史列表展示用） */
    private Integer stageOrder;
    /** 发起人：request 为接单创作者，self 为发布者 */
    private Long creatorId;
    private Integer days;
    private Integer hours;
    private String reason;
    private String status;
    /** 延时来源 request/self */
    private String applyType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date appliedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date decidedAt;

    private Long decidedBy;

    /** 本次动作生效后的轮次截止时间：自主延时/申请通过时写入，待处理与驳回为空 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dueAfter;

    public Long getApplyId() { return applyId; }
    public void setApplyId(Long applyId) { this.applyId = applyId; }
    public Long getDemandId() { return demandId; }
    public void setDemandId(Long demandId) { this.demandId = demandId; }
    public Long getStageId() { return stageId; }
    public void setStageId(Long stageId) { this.stageId = stageId; }
    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApplyType() { return applyType; }
    public void setApplyType(String applyType) { this.applyType = applyType; }
    public Date getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Date appliedAt) { this.appliedAt = appliedAt; }
    public Date getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Date decidedAt) { this.decidedAt = decidedAt; }
    public Long getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
    public Date getDueAfter() { return dueAfter; }
    public void setDueAfter(Date dueAfter) { this.dueAfter = dueAfter; }
}
