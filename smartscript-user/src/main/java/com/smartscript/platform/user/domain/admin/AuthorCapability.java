package com.smartscript.platform.user.domain.admin;

import java.util.Date;

/**
 * A4 PC 管理域：作者能力（契约 §3.3）。
 *
 * 映射自 user_author_capability（user_id 唯一）。该对象**不改变 userType**，
 * 作者能力与账号类型是两个独立维度，接口不得隐式改写 sys_user.user_type。
 */
public class AuthorCapability
{
    private Long userId;
    private String nickname;
    private String phoneMasked;
    private Boolean enabled;
    private String reason;
    private String operatorName;
    private Date updatedAt;

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

    public Boolean getEnabled()
    {
        return enabled;
    }

    public void setEnabled(Boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getOperatorName()
    {
        return operatorName;
    }

    public void setOperatorName(String operatorName)
    {
        this.operatorName = operatorName;
    }

    public Date getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt)
    {
        this.updatedAt = updatedAt;
    }
}
