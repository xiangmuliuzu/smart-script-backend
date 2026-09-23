package com.smartscript.platform.user.domain;

import java.util.Date;

/**
 * App 用户域：我的反馈（规格 §8.7，映射 user_feedback）。
 *
 * 状态集合与 A4 状态机、DB CHECK 约束 ck_user_feedback_status 一致：
 * SUBMITTED / PROCESSING / REPLIED / CLOSED。
 */
public class UserFeedback
{
    private Long feedbackId;
    private Long userId;
    private String category;
    private String content;
    private String status;
    private String reply;
    private Date submittedAt;
    private Date handledAt;
    private String attachmentRef;

    public Long getFeedbackId()
    {
        return feedbackId;
    }

    public void setFeedbackId(Long feedbackId)
    {
        this.feedbackId = feedbackId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getReply()
    {
        return reply;
    }

    public void setReply(String reply)
    {
        this.reply = reply;
    }

    public Date getSubmittedAt()
    {
        return submittedAt;
    }

    public void setSubmittedAt(Date submittedAt)
    {
        this.submittedAt = submittedAt;
    }

    public Date getHandledAt()
    {
        return handledAt;
    }

    public void setHandledAt(Date handledAt)
    {
        this.handledAt = handledAt;
    }

    public String getAttachmentRef()
    {
        return attachmentRef;
    }

    public void setAttachmentRef(String attachmentRef)
    {
        this.attachmentRef = attachmentRef;
    }
}
