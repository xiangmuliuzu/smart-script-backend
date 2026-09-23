package com.smartscript.platform.user.dto;

/**
 * A5 通知偏好条目（契约 §1.5）。
 *
 * 一个「渠道 + 类型」组合的开关。站内信（INBOX）与推送（PUSH）相互独立，
 * 关闭推送不影响站内消息的投递。
 */
public class NotificationPreferenceDto
{
    private String channel;
    private String type;
    private boolean enabled = true;

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
