package com.smartscript.platform.user.domain.admin;

/**
 * A4 批量装配行：userId + 实名状态（取该用户最新一条申请）。
 */
public class AppUserRealNameRow
{
    private Long userId;
    private String status;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
