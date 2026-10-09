package com.smartscript.platform.content.dto;

/**
 * 作品-标签关联行（内部查询载体，非对外下发对象）。
 *
 * 用于按作品批量查询标签后回填 {@link AppWorkDto#setTags}，
 * 避免列表场景逐作品查询标签造成 N+1。
 *
 * @author xiangsipeng
 */
public class AppWorkTagDto
{
    /** 作品ID（sys_work_tag.work_id） */
    private Long workId;

    /** 标签ID */
    private Integer tagId;

    /** 标签名称 */
    private String tagName;

    /** 标签类型 */
    private String tagType;

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public Integer getTagId()
    {
        return tagId;
    }

    public void setTagId(Integer tagId)
    {
        this.tagId = tagId;
    }

    public String getTagName()
    {
        return tagName;
    }

    public void setTagName(String tagName)
    {
        this.tagName = tagName;
    }

    public String getTagType()
    {
        return tagType;
    }

    public void setTagType(String tagType)
    {
        this.tagType = tagType;
    }
}