package com.smartscript.platform.user.domain.admin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A4 PC 管理域：管理消息（契约 §3.4 AdminNotification）。
 *
 * 映射自 user_notification：
 *   body       -> content
 *   business_type / business_id -> A4 新增双列
 *   biz_ref    -> 兼容列；历史行在该列有值而双列为 NULL 时，
 *                 服务层按裁决将其映射为 businessType=null、businessId=biz_ref
 *
 * receiverCount 由收件人表按 notification_id 聚合 COUNT 派生（裁决 GAP-4），
 * 不设冗余计数列。
 */
public class AdminNotification
{
    private Long notificationId;
    private String requestId;
    private String type;
    private String title;
    private String content;
    private String businessType;
    private String businessId;
    private Integer receiverCount;
    private String createdBy;
    private Date createdAt;

    /** 详情才返回的脱敏收件人摘要；列表保持为空。 */
    private List<NotificationReceiverBrief> receivers = new ArrayList<>();

    public Long getNotificationId()
    {
        return notificationId;
    }

    public void setNotificationId(Long notificationId)
    {
        this.notificationId = notificationId;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
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

    public Integer getReceiverCount()
    {
        return receiverCount;
    }

    public void setReceiverCount(Integer receiverCount)
    {
        this.receiverCount = receiverCount;
    }

    public String getCreatedBy()
    {
        return createdBy;
    }

    public void setCreatedBy(String createdBy)
    {
        this.createdBy = createdBy;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }

    public List<NotificationReceiverBrief> getReceivers()
    {
        return receivers;
    }

    public void setReceivers(List<NotificationReceiverBrief> receivers)
    {
        this.receivers = receivers == null ? new ArrayList<>() : receivers;
    }
}
