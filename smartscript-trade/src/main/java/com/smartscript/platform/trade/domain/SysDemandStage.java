package com.smartscript.platform.trade.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * C module: demand acceptance stage entity (sys_demand_stage).
 * Each demand may have multiple stages; buyer configures at publish time,
 * creator delivers per stage, buyer reviews and approves to trigger payment release.
 */
public class SysDemandStage
{
    public static final String STAGE_STATUS_PENDING   = "pending";
    /** 交付物 AI 审核中（提交后、人工验收前的自动前置门，期间暂停计时） */
    public static final String STAGE_STATUS_AI_REVIEWING = "ai_reviewing";
    public static final String STAGE_STATUS_SUBMITTED = "submitted";
    public static final String STAGE_STATUS_APPROVED  = "approved";
    public static final String STAGE_STATUS_REJECTED  = "rejected";

    /** 延期申请状态（sys_demand_stage.extend_status）：NULL=无申请 */
    public static final String EXTEND_PENDING  = "pending";
    public static final String EXTEND_APPROVED = "approved";
    public static final String EXTEND_REJECTED = "rejected";

    private Long stageId;
    private Long demandId;
    private Integer stageOrder;
    private Integer episodeTo;
    private BigDecimal amount;
    private String status;
    private String deliverableDesc;
    private String deliverableUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submittedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewedAt;

    private String rejectReason;
    private Integer releaseFlag;

    /** 逐轮独立计时：本轮时限(天/小时)、开始与截止时间、逾期标记 */
    private Integer durationDays;
    private Integer durationHours;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dueAt;

    private Integer overdue;

    /** 交付作品（作者本人的 sys_work；on_shelf 作品禁止交付，服务层校验） */
    private Long workId;
    /** 交付作品标题（JOIN sys_work 透传，只读展示用） */
    private String workTitle;

    /** AI 审核得分（0-100，NULL=未审核）：>=60 自动进入人工验收，<60 自动打回 */
    private Integer aiScore;
    /** AI 审核结论摘要（JSON：level/keywords/issues/usingMock） */
    private String aiResult;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date aiReviewedAt;

    /** 延期申请（最近一次快照）：状态 pending/approved/rejected，NULL=无申请；完整历史见 sys_demand_extend_apply */
    private String extendStatus;
    private Integer extendDays;
    private Integer extendHours;
    private String extendReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date extendAppliedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    private String createBy;
    private String updateBy;
    private String remark;

    /* ==================== getters / setters ==================== */

    public Long getStageId() { return stageId; }
    public void setStageId(Long stageId) { this.stageId = stageId; }
    public Long getDemandId() { return demandId; }
    public void setDemandId(Long demandId) { this.demandId = demandId; }
    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }
    public Integer getEpisodeTo() { return episodeTo; }
    public void setEpisodeTo(Integer episodeTo) { this.episodeTo = episodeTo; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDeliverableDesc() { return deliverableDesc; }
    public void setDeliverableDesc(String deliverableDesc) { this.deliverableDesc = deliverableDesc; }
    public String getDeliverableUrl() { return deliverableUrl; }
    public void setDeliverableUrl(String deliverableUrl) { this.deliverableUrl = deliverableUrl; }
    public Date getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Date submittedAt) { this.submittedAt = submittedAt; }
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Integer getReleaseFlag() { return releaseFlag; }
    public void setReleaseFlag(Integer releaseFlag) { this.releaseFlag = releaseFlag; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Integer getDurationHours() { return durationHours; }
    public void setDurationHours(Integer durationHours) { this.durationHours = durationHours; }
    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }
    public Date getDueAt() { return dueAt; }
    public void setDueAt(Date dueAt) { this.dueAt = dueAt; }
    public Integer getOverdue() { return overdue; }
    public void setOverdue(Integer overdue) { this.overdue = overdue; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getWorkTitle() { return workTitle; }
    public void setWorkTitle(String workTitle) { this.workTitle = workTitle; }
    public Integer getAiScore() { return aiScore; }
    public void setAiScore(Integer aiScore) { this.aiScore = aiScore; }
    public String getAiResult() { return aiResult; }
    public void setAiResult(String aiResult) { this.aiResult = aiResult; }
    public Date getAiReviewedAt() { return aiReviewedAt; }
    public void setAiReviewedAt(Date aiReviewedAt) { this.aiReviewedAt = aiReviewedAt; }
    public String getExtendStatus() { return extendStatus; }
    public void setExtendStatus(String extendStatus) { this.extendStatus = extendStatus; }
    public Integer getExtendDays() { return extendDays; }
    public void setExtendDays(Integer extendDays) { this.extendDays = extendDays; }
    public Integer getExtendHours() { return extendHours; }
    public void setExtendHours(Integer extendHours) { this.extendHours = extendHours; }
    public String getExtendReason() { return extendReason; }
    public void setExtendReason(String extendReason) { this.extendReason = extendReason; }
    public Date getExtendAppliedAt() { return extendAppliedAt; }
    public void setExtendAppliedAt(Date extendAppliedAt) { this.extendAppliedAt = extendAppliedAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
