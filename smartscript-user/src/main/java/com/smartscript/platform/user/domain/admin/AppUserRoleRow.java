package com.smartscript.platform.user.domain.admin;

/**
 * A4 批量装配行：userId + 角色编码。
 * 仅供列表装配使用，避免对每个用户逐行查询角色造成 N+1。
 */
public class AppUserRoleRow
{
    private Long userId;
    private String roleCode;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getRoleCode()
    {
        return roleCode;
    }

    public void setRoleCode(String roleCode)
    {
        this.roleCode = roleCode;
    }
}
