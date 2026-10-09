package com.smartscript.platform.content.dto;

/**
 * 作品标签（App 侧只读下发对象）。
 *
 * 依据：云端 script_platform_dev 库 sys_tag 表 + sys_work_tag 关联表。
 * 仅下发展示/筛选所需字段，不下发 use_count/sort/status 等内部字段
 * （status 已作为过滤条件下沉到 SQL，不下发）。
 *
 * @author xiangsipeng
 */
public class AppTagDto
{
    /** 标签ID */
    private Integer tagId;

    /** 标签名称 */
    private String tagName;

    /** 标签类型 */
    private String tagType;

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