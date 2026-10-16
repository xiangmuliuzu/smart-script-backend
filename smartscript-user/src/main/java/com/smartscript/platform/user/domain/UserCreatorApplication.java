package com.smartscript.platform.user.domain;

import java.util.Date;

/**
 * App 用户域：创作者申请（映射 user_creator_application）。
 *
 * 与实名申请同构：驳回后重提新增行，最新申请 = 最大 id，user_id 上不设唯一键。
 * 本对象承载申请人填写的四个自由字段与审核结果；审计字段（auditor_id/audited_at/
 * reject_reason）由审核端写入。
 */
public class UserCreatorApplication
{
    private Long applicationId;
    private Long userId;
    private String penName;
    private String direction;
    private String portfolio;
    private String intro;
    private String contact;
    private String status;
    private String rejectReason;
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
