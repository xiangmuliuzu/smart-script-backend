package com.smartscript.platform.user.dto;

/**
 * A5 个人资料更新请求（契约 §1.2）。
 *
 * 只接受昵称与头像。手机号、角色、实名状态**不在本对象内**，
 * 因此无法经该接口修改（规格 §8.3）。字段缺省表示不修改。
 */
public class UserProfileUpdateRequest
{
    private String nickname;
    private String avatar;

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
}
