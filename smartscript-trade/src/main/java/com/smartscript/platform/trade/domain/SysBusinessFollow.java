package com.smartscript.platform.trade.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * C module: business follow-up record (sys_business_follow).
 * Tracks interactions with partners: phone calls, emails, meetings.
 */
public class SysBusinessFollow extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long followId;
    private Long partnerId;
    private Long followerId;
    private String followType;
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date nextFollowDate;

    private String nextFollowContent;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date followTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** Transient: partner name joined from sys_partner */
    private String partnerName;
    /** Transient: follower name joined from sys_user */
    private String followerName;

    public Long getFollowId() { return followId; }
    public void setFollowId(Long followId) { this.followId = followId; }
    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    public Long getFollowerId() { return followerId; }
    public void setFollowerId(Long followerId) { this.followerId = followerId; }
    public String getFollowType() { return followType; }
    public void setFollowType(String followType) { this.followType = followType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Date getNextFollowDate() { return nextFollowDate; }
    public void setNextFollowDate(Date nextFollowDate) { this.nextFollowDate = nextFollowDate; }
    public String getNextFollowContent() { return nextFollowContent; }
    public void setNextFollowContent(String nextFollowContent) { this.nextFollowContent = nextFollowContent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getFollowTime() { return followTime; }
    public void setFollowTime(Date followTime) { this.followTime = followTime; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
    public String getFollowerName() { return followerName; }
    public void setFollowerName(String followerName) { this.followerName = followerName; }
}
