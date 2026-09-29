package com.smartscript.platform.content.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 标签对象 sys_tag
 *
 * 依据：云端 script_platform_dev 库 SHOW CREATE TABLE sys_tag（与附件5.1 表3-12 一致）。
 * 表注释：标签表。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at/updated_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （create_by/create_time/update_by/update_time/remark，继承 BaseEntity）。
 * 2. status 为 tinyint，Entity 用 String（若依字典风格），JDBC 自动转换。
 * 3. 主键 tag_id 为 int，Entity 用 Long（若依惯例）。
 *
 * @author xiangsipeng
 */
public class SysTag extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 标签ID */
    private Long tagId;

    /** 标签名称 */
    private String tagName;

    /** 标签类型 */
    private String tagType;

    /** 使用数量 */
    private Integer useCount;

    /** 显示顺序 */
    private Integer sort;

    /** 状态（0=正常 1=停用） */
    private String status;

    public SysTag()
    {
    }

    public Long getTagId()
    {
        return tagId;
    }

    public void setTagId(Long tagId)
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

    public Integer getUseCount()
    {
        return useCount;
    }

    public void setUseCount(Integer useCount)
    {
        this.useCount = useCount;
    }

    public Integer getSort()
    {
        return sort;
    }

    public void setSort(Integer sort)
    {
        this.sort = sort;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("tagId", getTagId())
                .append("tagName", getTagName())
                .append("tagType", getTagType())
                .append("useCount", getUseCount())
                .append("sort", getSort())
                .append("status", getStatus())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
