package com.smartscript.platform.user.domain.admin;

/**
 * A4 PC 管理域：可授予 App 用户的角色选项（契约 §5.1）。
 *
 * 由 `GET /api/v1/admin/app-users/grantable-roles` 返回，字段限于
 * roleId / roleName / roleKey，供前端授权弹窗选择。
 *
 * 数据来源为 sys_role.app_grantable 唯一权威标记（003 迁移引入），
 * 且必须同时满足启用、未删除、非超级管理员三个条件。
 */
public class GrantableRole
{
    private Long roleId;
    private String roleName;
    private String roleKey;

    public Long getRoleId()
    {
        return roleId;
    }

    public void setRoleId(Long roleId)
    {
        this.roleId = roleId;
    }

    public String getRoleName()
    {
        return roleName;
    }

    public void setRoleName(String roleName)
    {
        this.roleName = roleName;
    }

    public String getRoleKey()
    {
        return roleKey;
    }

    public void setRoleKey(String roleKey)
    {
        this.roleKey = roleKey;
    }
}
