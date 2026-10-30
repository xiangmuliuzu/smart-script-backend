package com.smartscript.platform.content.dto;

/**
 * AI 分镜生成请求（E 模块）。
 *
 * 「AI 剧本 → AI 分镜」一条龙：用户产出（或直接提供）剧本文本后，
 * 由 AI 导演（可选 skill）把剧本拆解为可直接用于拍摄/出售的分镜脚本。
 *
 * 入参：{script 必填, style 可选, work_id 可选, skill_id 可选}。
 *
 * @author xiangsipeng
 */
public class AppAiStoryboardRequest
{
    /** 剧本文本（必填；可以是 AI 剧本产出，也可以是用户自带的剧本） */
    private String script;

    /** 分镜风格（可选，如：电影感、短剧竖屏、动漫风） */
    private String style;

    /** 关联作品ID（可选） */
    private Long workId;

    /** 导演(skill) ID（可选；命中则把 skill 内容注入 system prompt） */
    private Long skillId;

    public String getScript()
    {
        return script;
    }

    public void setScript(String script)
    {
        this.script = script;
    }

    public String getStyle()
    {
        return style;
    }

    public void setStyle(String style)
    {
        this.style = style;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public Long getSkillId()
    {
        return skillId;
    }

    public void setSkillId(Long skillId)
    {
        this.skillId = skillId;
    }
}
