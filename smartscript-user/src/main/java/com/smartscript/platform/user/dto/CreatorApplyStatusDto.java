package com.smartscript.platform.user.dto;

import java.util.Date;

/**
 * A5 创作者申请状态（契约 §1.6）。
 *
 * 状态机：NOT_SUBMITTED -> PENDING -> APPROVED；PENDING -> REJECTED -> PENDING（驳回后重提）。
 * 回带申请人填写的字段，便于 App 在待审核/已驳回时回显，无需再请求详情接口。
 */
public class CreatorApplyStatusDto
{
    private String status;
    private String penName;
    private String direction;
    private String portfolio;
    private String intro;
    private String contact;
    private String rejectReason;
    private Date submittedAt;
    private Date reviewedAt;

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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
