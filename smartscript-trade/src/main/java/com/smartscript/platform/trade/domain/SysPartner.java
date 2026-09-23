package com.smartscript.platform.trade.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * C module: partner entity (sys_partner).
 * Cooperation partners: investors, studios, distribution platforms.
 */
public class SysPartner extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long partnerId;
    private String partnerNo;
    private String partnerName;
    private String partnerType;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private String demandTags;
    private Integer cooperationCount;
    private BigDecimal totalAmount;
    private String status;

    /** Auto-managed timestamp in DB (not BaseEntity) */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    public String getPartnerNo() { return partnerNo; }
    public void setPartnerNo(String partnerNo) { this.partnerNo = partnerNo; }
    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
    public String getPartnerType() { return partnerType; }
    public void setPartnerType(String partnerType) { this.partnerType = partnerType; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getDemandTags() { return demandTags; }
    public void setDemandTags(String demandTags) { this.demandTags = demandTags; }
    public Integer getCooperationCount() { return cooperationCount; }
    public void setCooperationCount(Integer cooperationCount) { this.cooperationCount = cooperationCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
