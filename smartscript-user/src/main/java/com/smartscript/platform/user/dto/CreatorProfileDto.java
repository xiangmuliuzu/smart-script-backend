package com.smartscript.platform.user.dto;

import java.util.List;

/**
 * A4 创作者资料只读投影（契约 §5.2 creatorProfile）。
 *
 * 无作者能力的用户在 UserProfileDto 中返回 null；有能力但资料未填写时
 * 返回对象本身，字段为 null / 空列表，由前端显示「未填写」空态。
 * 不含演示笔名或虚构类型。
 */
public class CreatorProfileDto
{
    private String penName;

    /** 擅长创作类型；空列表返回 []，不返回 null。 */
    private List<Specialty> specialties;

    /** 创作者介绍；null 表示未填写。 */
    private String introduction;

    public String getPenName()
    {
        return penName;
    }

    public void setPenName(String penName)
    {
        this.penName = penName;
    }

    public List<Specialty> getSpecialties()
    {
        return specialties;
    }

    public void setSpecialties(List<Specialty> specialties)
    {
        this.specialties = specialties;
    }

    public String getIntroduction()
    {
        return introduction;
    }

    public void setIntroduction(String introduction)
    {
        this.introduction = introduction;
    }

    /** 类型代码与名称；字典来源待项目确认，未知代码 name 回退为代码本身。 */
    public static class Specialty
    {
        private String code;
        private String name;

        public Specialty()
        {
        }

        public Specialty(String code, String name)
        {
            this.code = code;
            this.name = name;
        }

        public String getCode()
        {
            return code;
        }

        public void setCode(String code)
        {
            this.code = code;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }
    }
}
