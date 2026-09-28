package com.smartscript.platform.trade.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * C module: offline/online cooperation record (sys_offline_cooperation).
 * source = online -> 线上合作意向（分工 15）; source = offline -> 线下谈判（分工 16）。
 * 谈判时间复用 next_follow_at；地点/联系人/联系方式为 007 迁移新增列。
 */
public class SysOfflineCooperation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long cooperationId;
    private String cooperationNo;
    private Long workId;
    private Long creatorId;
    private Long partnerId;
    private Long contactId;
    private String source;
    private String status;
    private BigDecimal expectedAmount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextFollowAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastFollowAt;

    private String negotiationPlace;
    private String contactPerson;
    private String contactValue;
    private Long operatorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** Transient: work title joined from sys_work */
    private String workTitle;
    /** Transient: partner name joined from sys_partner */
    private String partnerName;
    /** Transient: creator nickname joined from sys_user */
    private String creatorName;
    /** Transient: unified fuzzy keyword for list filtering (not a column) */
    private String keyword;

    public Long getCooperationId() { return cooperationId; }
    public void setCooperationId(Long cooperationId) { this.cooperationId = cooperationId; }
    public String getCooperationNo() { return cooperationNo; }
    public void setCooperationNo(String cooperationNo) { this.cooperationNo = cooperationNo; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }
    public Date getNextFollowAt() { return nextFollowAt; }
    public void setNextFollowAt(Date nextFollowAt) { this.nextFollowAt = nextFollowAt; }
    public Date getLastFollowAt() { return lastFollowAt; }
    public void setLastFollowAt(Date lastFollowAt) { this.lastFollowAt = lastFollowAt; }
    public String getNegotiationPlace() { return negotiationPlace; }
    public void setNegotiationPlace(String negotiationPlace) { this.negotiationPlace = negotiationPlace; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getContactValue() { return contactValue; }
    public void setContactValue(String contactValue) { this.contactValue = contactValue; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getWorkTitle() { return workTitle; }
    public void setWorkTitle(String workTitle) { this.workTitle = workTitle; }
    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
