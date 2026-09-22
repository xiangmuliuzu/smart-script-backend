package com.smartscript.platform.user.dto;

/**
 * A5 个人资料（契约 §1.2）。
 *
 * 只暴露 sys_user 已存在的展示字段；手机号始终为掩码，实名状态来自
 * user_real_name_auth 最新记录。不含身份证号、明文手机号等敏感字段。
 */
public class UserProfileDto
{
    private Long userId;
    private String nickname;
    private String avatar;
    private String phoneMasked;
    private String userType;
    private String realNameStatus;

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

    public String getUserType()
    {
        return userType;
    }

    public void setUserType(String userType)
    {
        this.userType = userType;
    }

    public String getRealNameStatus()
    {
        return realNameStatus;
    }

    public void setRealNameStatus(String realNameStatus)
    {
        this.realNameStatus = realNameStatus;
    }
}
