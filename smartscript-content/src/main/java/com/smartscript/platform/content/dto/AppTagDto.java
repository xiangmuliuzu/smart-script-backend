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

    /** 标签维度（role 角色 / plot 情节） */
    private String tagType;

    /** 标签分组 */
    private String groupName;

    /** 所属频道ID（NULL=通用） */
    private Long channelId;

    /** 使用数量 */
    private Integer useCount;

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

    public String getGroupName()
    {
        return groupName;
    }

    public void setGroupName(String groupName)
    {
        this.groupName = groupName;
    }

    public Long getChannelId()
    {
        return channelId;
    }

    public void setChannelId(Long channelId)
    {
        this.channelId = channelId;
    }

    public Integer getUseCount()
    {
        return useCount;
    }

    public void setUseCount(Integer useCount)
    {
        this.useCount = useCount;
    }
}