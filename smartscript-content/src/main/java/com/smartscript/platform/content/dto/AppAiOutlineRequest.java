package com.smartscript.platform.content.dto;

/**
 * AI 大纲生成请求（B 模块 2.9.13 表2-123）。
 *
 * 依据：接口文档 2.9.13 输入参数。契约入参 {inspiration, genre, style, work_id} 均标注可选。
 *
 * 反推处理点：四个入参均非必填，故不做「至少其一非空」的额外限制，按契约原样透传给 AI 服务。
 *
 * @author xiangsipeng
 */
public class AppAiOutlineRequest
{
    /** 灵感描述（可选） */
    private String inspiration;

    /** 题材（可选） */
    private String genre;

    /** 风格（可选） */
    private String style;

    /** 关联作品ID（可选） */
    private Long workId;

    public String getInspiration()
    {
        return inspiration;
    }

    public void setInspiration(String inspiration)
    {
        this.inspiration = inspiration;
    }

    public String getGenre()
    {
        return genre;
    }

    public void setGenre(String genre)
    {
        this.genre = genre;
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
}