package com.smartscript.platform.trade.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * C module: quote/negotiation entity (sys_quote).
 * Seller submits a quote; buyer may counter-offer (negotiation history preserved).
 */
public class SysQuote extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long quoteId;
    private String quoteNo;
    private Long inquiryId;
    private Long sellerId;
    private Long quoterId;
    private String quoterRole;
    private BigDecimal price;
    private String licenseType;
    private Integer validDays;
    private String description;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** Transient: inquiry number */
    private String inquiryNo;
    /** Transient: work title */
    private String workTitle;
    /** Transient: quoter name */
    private String quoterName;

    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public String getQuoteNo() { return quoteNo; }
    public void setQuoteNo(String quoteNo) { this.quoteNo = quoteNo; }
    public Long getInquiryId() { return inquiryId; }
    public void setInquiryId(Long inquiryId) { this.inquiryId = inquiryId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Long getQuoterId() { return quoterId; }
    public void setQuoterId(Long quoterId) { this.quoterId = quoterId; }
    public String getQuoterRole() { return quoterRole; }
    public void setQuoterRole(String quoterRole) { this.quoterRole = quoterRole; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public Integer getValidDays() { return validDays; }
    public void setValidDays(Integer validDays) { this.validDays = validDays; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getExpireAt() { return expireAt; }
    public void setExpireAt(Date expireAt) { this.expireAt = expireAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getInquiryNo() { return inquiryNo; }
    public void setInquiryNo(String inquiryNo) { this.inquiryNo = inquiryNo; }
    public String getWorkTitle() { return workTitle; }
    public void setWorkTitle(String workTitle) { this.workTitle = workTitle; }
    public String getQuoterName() { return quoterName; }
    public void setQuoterName(String quoterName) { this.quoterName = quoterName; }
}
