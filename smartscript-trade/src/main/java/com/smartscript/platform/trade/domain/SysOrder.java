package com.smartscript.platform.trade.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * C module: order entity (sys_order).
 * Authorization order generated from accepted quote or inquiry conversion.
 * C owns order creation + status flow up to handoff; contract/escrow/settlement belongs to D.
 */
public class SysOrder extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String orderNo;
    private Long contractId;
    private Long escrowId;
    private Long inquiryId;
    private Long quoteId;
    private Long workId;
    private Long buyerId;
    private Long sellerId;
    private String orderType;
    private String licenseType;
    private BigDecimal totalAmount;
    private BigDecimal platformFee;
    private BigDecimal splitRate;
    private BigDecimal sellerAmount;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date payTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date completeTime;

    private String extJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** Transient: work title */
    private String workTitle;
    /** Transient: buyer name */
    private String buyerName;
    /** Transient: seller name */
    private String sellerName;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }
    public Long getEscrowId() { return escrowId; }
    public void setEscrowId(Long escrowId) { this.escrowId = escrowId; }
    public Long getInquiryId() { return inquiryId; }
    public void setInquiryId(Long inquiryId) { this.inquiryId = inquiryId; }
    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public void setPlatformFee(BigDecimal platformFee) { this.platformFee = platformFee; }
    public BigDecimal getSplitRate() { return splitRate; }
    public void setSplitRate(BigDecimal splitRate) { this.splitRate = splitRate; }
    public BigDecimal getSellerAmount() { return sellerAmount; }
    public void setSellerAmount(BigDecimal sellerAmount) { this.sellerAmount = sellerAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }
    public Date getCompleteTime() { return completeTime; }
    public void setCompleteTime(Date completeTime) { this.completeTime = completeTime; }
    public String getExtJson() { return extJson; }
    public void setExtJson(String extJson) { this.extJson = extJson; }
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
