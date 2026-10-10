package com.smartscript.platform.review.domain;

import java.util.Date;

/**
 * 举报/违规记录
 */
public class Report {
    private Long reportId;
    private Long userId;
    private String targetType;
    private Long targetId;
    private String reason;
    private String description;
    private String status;
    private Long handlerId;
    private String handleResult;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String remark;

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
    public String getHandleResult() { return handleResult; }
    public void setHandleResult(String handleResult) { this.handleResult = handleResult; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
