package com.smartscript.platform.user.domain.admin;

import java.util.Date;

/**
 * A4 消息收件人脱敏摘要（契约 §3.4 / §5.4）。
 *
 * 只含 userId、昵称、掩码手机号与已读时间。消息详情返回该摘要，
 * 不回传全量敏感用户资料。
 */
public class NotificationReceiverBrief
{
    private Long userId;
    private String nickname;
    private String phoneMasked;
    private Date readAt;

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

    public Date getReadAt()
    {
        return readAt;
    }

    public void setReadAt(Date readAt)
    {
        this.readAt = readAt;
    }
}
