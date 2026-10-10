package com.smartscript.platform.content.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/** 印章审核与状态操作记录。 */
public class SysCopyrightSealLog
{
    private Long logId;
    private Long sealId;
    private String operationType;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private Long operatorId;
    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public Long getSealId() { return sealId; }
    public void setSealId(Long sealId) { this.sealId = sealId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
