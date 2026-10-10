package com.smartscript.platform.user.dto;

/**
 * A5 个人资料（契约 §1.2；A4 用户中心增量扩展见开发文档 §5.2）。
 *
 * 只暴露 sys_user 已存在的展示字段；手机号始终为掩码，实名状态来自
 * user_real_name_auth 最新记录。不含身份证号、明文手机号等敏感字段。
 */
public class UserProfileDto
{
    private Long userId;
    private String nickname;
    private String avatar;

    /** 个人简介；null 表示未填写。 */
    private String bio;
    private String phoneMasked;
    private String userType;
    private String realNameStatus;

    /**
     * 注册时间（A4 增量）：sys_user.create_time，ISO 8601 带时区字符串。
     * 数据库会话时区为 GMT+8，因此历史值统一按 +08:00 标注；
     * 缺失的历史值返回 null，由前端显示「—」，不拼接伪时区。
     */
    private String registeredAt;

    /**
     * 账号状态（A4 增量）：sys_user.status 原始代码（0 正常 / 1 停用）。
     * 返回原代码而不做文案转换，未知代码由前端显示「状态未知」。
     */
    private String accountStatus;

    /**
     * 创作者资料只读投影（A4 增量）：无作者能力返回 null；
     * 有能力但未填写时返回对象本身，字段为 null / 空列表。
     */
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

    public String getRegisteredAt()
    {
        return registeredAt;
    }

    public void setRegisteredAt(String registeredAt)
    {
        this.registeredAt = registeredAt;
    }

    public String getAccountStatus()
    {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus)
    {
        this.accountStatus = accountStatus;
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
