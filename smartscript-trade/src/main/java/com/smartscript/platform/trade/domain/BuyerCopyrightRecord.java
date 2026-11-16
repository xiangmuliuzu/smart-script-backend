package com.smartscript.platform.trade.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 买方「版权」列表行：已完成交割中的 cert 文件。
 */
public class BuyerCopyrightRecord
{
    private Long fileId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private Long workId;
    private String workTitle;
    private String workCover;
    private Long sellerId;
    private String sellerName;
    private Long deliveryId;
    private String deliveryNo;
    private Long orderId;
    private String orderNo;
    private Long buyerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date completedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date confirmTime;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getWorkTitle() { return workTitle; }
    public void setWorkTitle(String workTitle) { this.workTitle = workTitle; }
    public String getWorkCover() { return workCover; }
    public void setWorkCover(String workCover) { this.workCover = workCover; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public Long getDeliveryId() { return deliveryId; }
    public void setDeliveryId(Long deliveryId) { this.deliveryId = deliveryId; }
    public String getDeliveryNo() { return deliveryNo; }
    public void setDeliveryNo(String deliveryNo) { this.deliveryNo = deliveryNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Date getCompletedTime() { return completedTime; }
    public void setCompletedTime(Date completedTime) { this.completedTime = completedTime; }
    public Date getConfirmTime() { return confirmTime; }
    public void setConfirmTime(Date confirmTime) { this.confirmTime = confirmTime; }
}
