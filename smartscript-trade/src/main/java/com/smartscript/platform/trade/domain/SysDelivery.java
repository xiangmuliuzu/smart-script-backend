package com.smartscript.platform.trade.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 版权交割单（sys_delivery）。
 */
public class SysDelivery extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING_UPLOAD = "pending_upload";
    public static final String STATUS_PENDING_CONFIRM = "pending_confirm";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    private Long deliveryId;
    private String deliveryNo;
    private Long orderId;
    private Long workId;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal amount;
    private String licenseType;
    private String status;
    private String contractNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date contractSignTime;

    private String certNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date confirmTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date completedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    private String orderNo;
    private String workTitle;
    private String workCover;
    private String buyerName;
    private String sellerName;
    private List<SysDeliveryFile> files;

    public Long getDeliveryId() { return deliveryId; }
    public void setDeliveryId(Long deliveryId) { this.deliveryId = deliveryId; }
    public String getDeliveryNo() { return deliveryNo; }
    public void setDeliveryNo(String deliveryNo) { this.deliveryNo = deliveryNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }
    public Date getContractSignTime() { return contractSignTime; }
    public void setContractSignTime(Date contractSignTime) { this.contractSignTime = contractSignTime; }
    public String getCertNo() { return certNo; }
    public void setCertNo(String certNo) { this.certNo = certNo; }
    public Date getUploadTime() { return uploadTime; }
    public void setUploadTime(Date uploadTime) { this.uploadTime = uploadTime; }
    public Date getConfirmTime() { return confirmTime; }
    public void setConfirmTime(Date confirmTime) { this.confirmTime = confirmTime; }
    public Date getCompletedTime() { return completedTime; }
    public void setCompletedTime(Date completedTime) { this.completedTime = completedTime; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getWorkTitle() { return workTitle; }
    public void setWorkTitle(String workTitle) { this.workTitle = workTitle; }
    public String getWorkCover() { return workCover; }
    public void setWorkCover(String workCover) { this.workCover = workCover; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public List<SysDeliveryFile> getFiles() { return files; }
    public void setFiles(List<SysDeliveryFile> files) { this.files = files; }
}
