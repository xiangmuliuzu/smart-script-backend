package com.smartscript.platform.user.domain;

/**
 * App 用户域：通知偏好（规格 §8.6，映射 user_notification_preference）。
 *
 * 存储为稀疏结构：只为用户显式改动过的 渠道+类型 组合落库，
 * 未落库的组合由服务层按默认值（开启）展开返回。
 */
public class UserNotificationPreference
{
    private Long id;
    private Long userId;
    private String channel;
    private String type;
    private boolean enabled;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}
