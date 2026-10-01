package com.smartscript.platform.trade.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * C module: order status transition log (sys_order_status_log).
 * Every status change is recorded as an immutable log entry.
 * Does NOT extend BaseEntity (no updateBy/updateTime - logs are append-only).
 */
public class SysOrderStatusLog implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    private Long logId;
    private Long orderId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorRole;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** Transient: operator display name */
    private String operatorName;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorRole() { return operatorRole; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
}
