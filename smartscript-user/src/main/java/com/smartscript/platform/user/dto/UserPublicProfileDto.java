package com.smartscript.platform.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 公开用户资料（个人主页，供任意登录用户查看他人主页）。
 *
 * 只暴露公开字段：不含手机号、实名状态、账号状态等私密信息。
 * 关注/粉丝为真实计数；isFollowing 表示「当前登录用户是否已关注该用户」；
 * isSelf 表示「被查看的是否就是当前登录用户本人」。
 */
public class UserPublicProfileDto
{
    private Long userId;
    private String nickname;
    private String avatar;
    private String bio;

    /** 账号类型原始代码：01 普通用户 / 02 创作者 / 03 甲方。 */
    private String userType;

    private String registeredAt;

    private int followerCount;
    private int followingCount;
    private boolean isFollowing;
    private boolean isSelf;

    /** 创作者资料投影；未开通作者能力时为 null（与 UserProfileDto 同口径）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CreatorProfileDto creatorProfile;

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

    public String getRegisteredAt()
    {
        return registeredAt;
    }

    public void setRegisteredAt(String registeredAt)
    {
        this.registeredAt = registeredAt;
    }

    public int getFollowerCount()
    {
        return followerCount;
    }

    public void setFollowerCount(int followerCount)
    {
        this.followerCount = followerCount;
    }

    public int getFollowingCount()
    {
        return followingCount;
    }

    public void setFollowingCount(int followingCount)
    {
        this.followingCount = followingCount;
    }

    public boolean getIsFollowing()
    {
        return isFollowing;
    }

    public void setFollowing(boolean following)
    {
        isFollowing = following;
    }

    public boolean getIsSelf()
    {
        return isSelf;
    }

    public void setSelf(boolean self)
    {
        isSelf = self;
    }

    public CreatorProfileDto getCreatorProfile()
    {
        return creatorProfile;
    }

    public void setCreatorProfile(CreatorProfileDto creatorProfile)
    {
        this.creatorProfile = creatorProfile;
    }
}
