package com.smartscript.platform.user.domain;

/**
 * A4 创作者资料投影（user_creator_profile，按 user_id 一对一）。
 *
 * 本次只读：资料维护来源由项目协作确认后接入，用户中心不提供写入端点，
 * 也不凭作品记录或账号类型推断笔名。
 */
public class UserCreatorProfile
{
    private Long userId;

    /** 笔名；null 表示未填写，与账号昵称相互独立。 */
    private String penName;

    /** 擅长创作类型代码，逗号分隔；null 或空串表示未填写。 */
    private String specialties;

    /** 创作者介绍；null 表示未填写。 */
    private String profileIntro;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getPenName()
    {
        return penName;
    }

    public void setPenName(String penName)
    {
        this.penName = penName;
    }

    public String getSpecialties()
    {
        return specialties;
    }

    public void setSpecialties(String specialties)
    {
        this.specialties = specialties;
    }

    public String getProfileIntro()
    {
        return profileIntro;
    }

    public void setProfileIntro(String profileIntro)
    {
        this.profileIntro = profileIntro;
    }
}
