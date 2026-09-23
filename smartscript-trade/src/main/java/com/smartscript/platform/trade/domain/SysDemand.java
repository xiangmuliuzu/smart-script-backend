package com.smartscript.platform.trade.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * C module: demand project entity (sys_demand).
 * Buyer publishes a demand/call-for-submissions; authors submit their works.
 */
public class SysDemand extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long demandId;
    private String demandNo;
    private Long clientId;
    private String title;
    private Integer genreId;
    private BigDecimal budget;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date deadline;

    private String requirement;
    private String contactInfo;
    private Integer submissionCount;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** Transient: client name */
    private String clientName;

    public Long getDemandId() { return demandId; }
    public void setDemandId(Long demandId) { this.demandId = demandId; }
    public String getDemandNo() { return demandNo; }
    public void setDemandNo(String demandNo) { this.demandNo = demandNo; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getGenreId() { return genreId; }
    public void setGenreId(Integer genreId) { this.genreId = genreId; }
    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }
    public Date getDeadline() { return deadline; }
    public void setDeadline(Date deadline) { this.deadline = deadline; }
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
    public Integer getSubmissionCount() { return submissionCount; }
    public void setSubmissionCount(Integer submissionCount) { this.submissionCount = submissionCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
}
