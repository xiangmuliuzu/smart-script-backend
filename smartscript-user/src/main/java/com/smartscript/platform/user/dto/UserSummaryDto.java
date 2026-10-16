package com.smartscript.platform.user.dto;

/**
 * 用户摘要（关注/粉丝列表项）。
 *
 * 只暴露公开字段；isFollowing 表示「当前登录用户是否已关注该用户」。
 */
public class UserSummaryDto
{
    private Long userId;
    private String nickname;
    private String avatar;
    private String bio;

    /** 账号类型原始代码：01 普通用户 / 02 创作者 / 03 甲方。 */
    private String userType;

    private boolean isFollowing;

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

    public String getBio()
    {
        return bio;
    }

    public void setBio(String bio)
    {
        this.bio = bio;
    }

    public String getUserType()
    {
        return userType;
    }

    public void setUserType(String userType)
    {
        this.userType = userType;
    }

    public boolean isFollowing()
    {
        return isFollowing;
    }

    public void setFollowing(boolean following)
    {
        isFollowing = following;
    }
}
