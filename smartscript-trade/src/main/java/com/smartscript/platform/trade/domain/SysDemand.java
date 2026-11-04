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
    private String tags;
    private BigDecimal budget;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date deadline;

    private String requirement;
    private String contactInfo;
    private Integer submissionCount;
    private Integer totalEpisodes;
    private String status;

    /** 接单制：抢单创作者、接单时间、完成时限(小时)、应完成时间、逾期标记 */
    private Long acceptedCreatorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date acceptedAt;

    private Integer durationHours;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dueAt;

    private Integer overdue;

    /** 托管/押金资金状态（入账走支付宝沙箱，出账记账） */
    private BigDecimal escrowAmount;
    private String escrowStatus;
    private BigDecimal depositAmount;
    private String depositStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date depositPaidAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date depositSettledAt;

    private String failReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date failAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** Transient: client name */
    private String clientName;

    /** Transient: accepted creator (接单人) name */
    private String acceptedCreatorName;

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
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
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
    public Integer getTotalEpisodes() { return totalEpisodes; }
    public void setTotalEpisodes(Integer totalEpisodes) { this.totalEpisodes = totalEpisodes; }
    public Long getAcceptedCreatorId() { return acceptedCreatorId; }
    public void setAcceptedCreatorId(Long acceptedCreatorId) { this.acceptedCreatorId = acceptedCreatorId; }
    public Date getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Date acceptedAt) { this.acceptedAt = acceptedAt; }
    public Integer getDurationHours() { return durationHours; }
    public void setDurationHours(Integer durationHours) { this.durationHours = durationHours; }
    public Date getDueAt() { return dueAt; }
    public void setDueAt(Date dueAt) { this.dueAt = dueAt; }
    public Integer getOverdue() { return overdue; }
    public void setOverdue(Integer overdue) { this.overdue = overdue; }
    public String getAcceptedCreatorName() { return acceptedCreatorName; }
    public void setAcceptedCreatorName(String acceptedCreatorName) { this.acceptedCreatorName = acceptedCreatorName; }
    public BigDecimal getEscrowAmount() { return escrowAmount; }
    public void setEscrowAmount(BigDecimal escrowAmount) { this.escrowAmount = escrowAmount; }
    public String getEscrowStatus() { return escrowStatus; }
    public void setEscrowStatus(String escrowStatus) { this.escrowStatus = escrowStatus; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public String getDepositStatus() { return depositStatus; }
    public void setDepositStatus(String depositStatus) { this.depositStatus = depositStatus; }
    public Date getDepositPaidAt() { return depositPaidAt; }
    public void setDepositPaidAt(Date depositPaidAt) { this.depositPaidAt = depositPaidAt; }
    public Date getDepositSettledAt() { return depositSettledAt; }
    public void setDepositSettledAt(Date depositSettledAt) { this.depositSettledAt = depositSettledAt; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public Date getFailAt() { return failAt; }
    public void setFailAt(Date failAt) { this.failAt = failAt; }

    /** Transient: unified fuzzy keyword for list filtering (not a column) */
    private String keyword;
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    // ==================== 任务广场查询临时字段（均非列，仅驱动分页/排序/筛选） ====================

    /** 分页：SQL LIMIT 行数（APP 真分页用；PC 走 PageHelper 不设置）。 */
    private Integer limit;
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    /** 分页：SQL OFFSET 偏移量。 */
    private Integer offset;
    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }

    /** 排序字段白名单键：created（默认）/ budget / deadline。 */
    private String sortBy;
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    /** 排序方向：asc / desc（由 mapper 的 choose 白名单落定，非法值回落默认）。 */
    private String sortDir;
    public String getSortDir() { return sortDir; }
    public void setSortDir(String sortDir) { this.sortDir = sortDir; }

    /** 预算区间筛选（元，与库列同口径；控制器已把 APP 传入的分转成元）。 */
    private java.math.BigDecimal budgetMin;
    public java.math.BigDecimal getBudgetMin() { return budgetMin; }
    public void setBudgetMin(java.math.BigDecimal budgetMin) { this.budgetMin = budgetMin; }

    private java.math.BigDecimal budgetMax;
    public java.math.BigDecimal getBudgetMax() { return budgetMax; }
    public void setBudgetMax(java.math.BigDecimal budgetMax) { this.budgetMax = budgetMax; }

    /** 集数下限筛选（total_episodes >= 该值）。 */
    private Integer episodesMin;
    public Integer getEpisodesMin() { return episodesMin; }
    public void setEpisodesMin(Integer episodesMin) { this.episodesMin = episodesMin; }
}
