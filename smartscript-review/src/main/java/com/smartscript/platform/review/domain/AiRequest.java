package com.smartscript.platform.review.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/**
 * AI调用请求实体（sys_ai_request）
 *
 * @author smartscript
 */
public class AiRequest {

    private Long requestId;
    /** 请求流水号 */
    private String requestNo;
    private Long userId;
    private Long workId;
    /** 能力：outline/writing/polish */
    private String capability;
    /** 消耗次数 */
    private Integer quotaCost;
    /** 状态：consumed/refunded/failed */
    private String status;
    private String errorCode;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getCapability() { return capability; }
    public void setCapability(String capability) { this.capability = capability; }
    public Integer getQuotaCost() { return quotaCost; }
    public void setQuotaCost(Integer quotaCost) { this.quotaCost = quotaCost; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }
    public Date getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Date finishedAt) { this.finishedAt = finishedAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
