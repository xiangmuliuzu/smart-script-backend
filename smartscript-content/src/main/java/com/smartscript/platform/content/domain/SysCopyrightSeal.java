package com.smartscript.platform.content.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class SysCopyrightSeal
{
    private Long sealId;
    private Long userId;
    private String sealName;
    private String sealImageUrl;
    private String materialUrls;
    private String reviewStatus;
    private String sealStatus;
    private String rejectReason;
    private Long reviewerId;
    private String userName;
    private String userAccount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getSealId() { return sealId; }
    public void setSealId(Long sealId) { this.sealId = sealId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSealName() { return sealName; }
    public void setSealName(String sealName) { this.sealName = sealName; }
    public String getSealImageUrl() { return sealImageUrl; }
    public void setSealImageUrl(String sealImageUrl) { this.sealImageUrl = sealImageUrl; }
    public String getMaterialUrls() { return materialUrls; }
    public void setMaterialUrls(String materialUrls) { this.materialUrls = materialUrls; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getSealStatus() { return sealStatus; }
    public void setSealStatus(String sealStatus) { this.sealStatus = sealStatus; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserAccount() { return userAccount; }
    public void setUserAccount(String userAccount) { this.userAccount = userAccount; }
    public Date getReviewTime() { return reviewTime; }
    public void setReviewTime(Date reviewTime) { this.reviewTime = reviewTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
