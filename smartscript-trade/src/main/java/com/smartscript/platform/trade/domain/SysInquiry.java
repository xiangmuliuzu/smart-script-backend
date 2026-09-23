package com.smartscript.platform.trade.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * C module: inquiry entity (sys_inquiry).
 * Buyer sends inquiry to seller for a specific work's licensing.
 */
public class SysInquiry extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long inquiryId;
    private String inquiryNo;
    private Long workId;
    private Long buyerId;
    private Long sellerId;
    private String licenseType;
    private String intendedUse;
    private BigDecimal budget;
    private String message;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** Transient: work title joined from sys_work */
    private String workTitle;
    /** Transient: buyer name */
    private String buyerName;
    /** Transient: seller name */
    private String sellerName;

    public Long getInquiryId() { return inquiryId; }
    public void setInquiryId(Long inquiryId) { this.inquiryId = inquiryId; }
    public String getInquiryNo() { return inquiryNo; }
    public void setInquiryNo(String inquiryNo) { this.inquiryNo = inquiryNo; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public String getIntendedUse() { return intendedUse; }
    public void setIntendedUse(String intendedUse) { this.intendedUse = intendedUse; }
    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getExpireAt() { return expireAt; }
    public void setExpireAt(Date expireAt) { this.expireAt = expireAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getWorkTitle() { return workTitle; }
    public void setWorkTitle(String workTitle) { this.workTitle = workTitle; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
}
