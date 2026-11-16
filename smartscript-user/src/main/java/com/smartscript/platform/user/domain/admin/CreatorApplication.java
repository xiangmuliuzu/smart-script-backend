package com.smartscript.platform.user.domain.admin;

import java.util.Date;

/**
 * A4 PC 管理域：创作者申请（契约 §3.3）。
 *
 * 映射自 user_creator_application：create_time -> submittedAt、audited_at -> reviewedAt、
 * auditor_id -> reviewerName（关联 sys_user.nick_name 取得）。列表与详情均返回申请人填写字段，
 * 手机号只以掩码形式出库。
 */
public class CreatorApplication
{
    private Long applicationId;
    private Long userId;
    private String nickname;
    private String phoneMasked;
    private String penName;
    private String direction;
    private String portfolio;
    private String intro;
    private String contact;
    private String status;
    private String rejectReason;
    private String reviewerName;
    private Date submittedAt;
    private Date reviewedAt;

    public Long getApplicationId()
    {
        return applicationId;
    }

    public void setApplicationId(Long applicationId)
    {
        this.applicationId = applicationId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getNickname()
    {
        return nickname;
    }

    public void setNickname(String nickname)
    {
        this.nickname = nickname;
    }

    public String getPhoneMasked()
    {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked)
    {
        this.phoneMasked = phoneMasked;
    }

    public String getPenName()
    {
        return penName;
    }

    public void setPenName(String penName)
    {
        this.penName = penName;
    }

    public String getDirection()
    {
        return direction;
    }

    public void setDirection(String direction)
    {
        this.direction = direction;
    }

    public String getPortfolio()
    {
        return portfolio;
    }

    public void setPortfolio(String portfolio)
    {
        this.portfolio = portfolio;
    }

    public String getIntro()
    {
        return intro;
    }

    public void setIntro(String intro)
    {
        this.intro = intro;
    }

    public String getContact()
    {
        return contact;
    }

    public void setContact(String contact)
    {
        this.contact = contact;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }

    public String getReviewerName()
    {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName)
    {
        this.reviewerName = reviewerName;
    }

    public Date getSubmittedAt()
    {
        return submittedAt;
    }

    public void setSubmittedAt(Date submittedAt)
    {
        this.submittedAt = submittedAt;
    }

    public Date getReviewedAt()
    {
        return reviewedAt;
    }

    public void setReviewedAt(Date reviewedAt)
    {
        this.reviewedAt = reviewedAt;
    }
}
