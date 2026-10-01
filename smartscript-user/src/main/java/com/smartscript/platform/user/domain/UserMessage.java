package com.smartscript.platform.user.domain;

import java.util.Date;

/**
 * App 用户域：站内消息收件箱行（规格 §8.6）。
 *
 * 由 user_notification JOIN user_notification_receiver 派生；
 * read 与 readAt 来自**当前用户的收件记录**，不是全局状态。
 */
public class UserMessage
{
    private Long messageId;
    private String type;
    private String title;
    private String content;
    private String businessType;
    private String businessId;
    private boolean read;
    private Date readAt;
    private Date createdAt;

    public Long getMessageId()
    {
        return messageId;
    }

    public void setMessageId(Long messageId)
    {
        this.messageId = messageId;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getBusinessType()
    {
        return businessType;
    }

    public void setBusinessType(String businessType)
    {
        this.businessType = businessType;
    }

    public String getBusinessId()
    {
        return businessId;
    }

    public void setBusinessId(String businessId)
    {
        this.businessId = businessId;
    }

    public boolean isRead()
    {
        return read;
    }

    public void setRead(boolean read)
    {
        this.read = read;
    }

    public Date getReadAt()
    {
        return readAt;
    }

    public void setReadAt(Date readAt)
    {
        this.readAt = readAt;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }
}
