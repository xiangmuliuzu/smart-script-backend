package com.smartscript.platform.user.domain.admin;

/**
 * A4 批量装配行：userId + 作者能力开关。
 */
public class AppUserCapabilityRow
{
    private Long userId;
    private Boolean enabled;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Boolean getEnabled()
    {
        return enabled;
    }

    public void setEnabled(Boolean enabled)
    {
        this.enabled = enabled;
    }
}
