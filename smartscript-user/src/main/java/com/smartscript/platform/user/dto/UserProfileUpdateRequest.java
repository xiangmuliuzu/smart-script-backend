package com.smartscript.platform.user.dto;

/**
 * A5 个人资料更新请求（契约 §1.2）。
 *
 * 只接受昵称、头像与简介。手机号、角色、实名状态**不在本对象内**，
 * 因此无法经该接口修改（规格 §8.3）；本对象也不含 userId 字段，
 * 只能修改当前登录用户本人（数据隔离）。字段缺省表示不修改。
 */
public class UserProfileUpdateRequest
{
    private String nickname;
    private String avatar;

    /** 个人简介；空串表示清空，缺省表示不修改。 */
    private String bio;

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

    public String getBio()
    {
        return bio;
    }

    public void setBio(String bio)
    {
        this.bio = bio;
    }
}
