package com.smartscript.platform.trade.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * C module: demand submission entity (sys_demand_submission).
 * An author submits a work in response to a demand project.
 */
public class SysDemandSubmission extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long submissionId;
    private Long demandId;
    private Long workId;
    private Long submitterId;
    private String status;
    private String submitMessage;
    private String reviewRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** Transient: work title */
    private String workTitle;
    /** Transient: submitter name */
    private String submitterName;

    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Long getDemandId() { return demandId; }
    public void setDemandId(Long demandId) { this.demandId = demandId; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public Long getSubmitterId() { return submitterId; }
    public void setSubmitterId(Long submitterId) { this.submitterId = submitterId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmitMessage() { return submitMessage; }
    public void setSubmitMessage(String submitMessage) { this.submitMessage = submitMessage; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getWorkTitle() { return workTitle; }
    public void setWorkTitle(String workTitle) { this.workTitle = workTitle; }
    public String getSubmitterName() { return submitterName; }
    public void setSubmitterName(String submitterName) { this.submitterName = submitterName; }
}
