package com.smartscript.platform.user.dto;

public class CurrentUserDto
{
    private Long userId;
    private String userType;
    private String nickname;
    private String avatar;
    private String phoneMasked;
    private String[] roles;

    /**
     * 权限标识集合（A6 起提供）。
     *
     * 供 App 侧做按钮/入口级授权判断（规格 §10 的 hasRole 等价能力）；
     * 接口侧仍必须独立校验权限，客户端判断只用于展示控制。
     */
    private String[] permissions;

    /**
     * 作者能力（A6 起提供）：表示账号是否已开通作者能力。
     *
     * 取自 user_author_capability，与角色、实名状态互相独立。
     */
    private boolean authorCapability;

    private String realNameStatus;

    /**
     * 是否已设置密码（A5 账号安全用）。
     *
     * 只暴露布尔值，不返回散列本身：客户端据此在「首次设置密码」与
     * 「修改已有密码」之间选择入口，避免用户提交后才发现状态冲突
     * （规格 §8.5「没有密码的用户走现有设置密码流程」）。
     */
    private boolean hasPassword;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserType()
    {
        return userType;
    }

    public void setUserType(String userType)
    {
        this.userType = userType;
    }

    public String getNickname()
    {
        return nickname;
    }

    public void setNickname(String nickname)
    {
        this.nickname = nickname;
    }

    public String getAvatar()
    {
        return avatar;
    }

    public void setAvatar(String avatar)
    {
        this.avatar = avatar;
    }

    public String getPhoneMasked()
    {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked)
    {
        this.phoneMasked = phoneMasked;
    }

    public String[] getRoles()
    {
        return roles;
    }

    public void setRoles(String[] roles)
    {
        this.roles = roles;
    }

    public String[] getPermissions()
    {
        return permissions;
    }

    public void setPermissions(String[] permissions)
    {
        this.permissions = permissions;
    }

    public boolean isAuthorCapability()
    {
        return authorCapability;
    }

    public void setAuthorCapability(boolean authorCapability)
    {
        this.authorCapability = authorCapability;
    }

    public String getRealNameStatus()
    {
        return realNameStatus;
    }

    public void setRealNameStatus(String realNameStatus)
    {
        this.realNameStatus = realNameStatus;
    }

    public boolean isHasPassword()
    {
        return hasPassword;
    }

    public void setHasPassword(boolean hasPassword)
    {
        this.hasPassword = hasPassword;
    }
}
