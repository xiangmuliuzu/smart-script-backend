package com.smartscript.platform.trade.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 版权交割文件（sys_delivery_file）。
 */
public class SysDeliveryFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    public static final String TYPE_CERT = "cert";
    public static final String TYPE_SOURCE = "source";
    public static final String TYPE_DOC = "doc";
    public static final String TYPE_OTHER = "other";

    private Long fileId;
    private Long deliveryId;
    private String fileName;
    private String fileType;
    private String fileUrl;
    private Long fileSize;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Long getDeliveryId() { return deliveryId; }
    public void setDeliveryId(Long deliveryId) { this.deliveryId = deliveryId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
}
