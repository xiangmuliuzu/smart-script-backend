package com.smartscript.platform.content.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 剧本分类对象 sys_category
 *
 * 依据：云端 script_platform_dev 库 SHOW CREATE TABLE sys_category（与附件5.1 表3-9 一致）。
 * 表注释：剧本分类表（APP 分类不包含外部视频）。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at/updated_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （create_by/create_time/update_by/update_time/remark，继承 BaseEntity）。
 * 2. status 为 tinyint，Entity 用 String（若依字典风格），JDBC 自动转换。
 * 3. 主键 category_id 为 int，Entity 用 Long（若依惯例）。
 *
 * @author xiangsipeng
 */
public class SysCategory extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分类ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 分类类型 */
    private String categoryType;

    /** 父级ID（0=顶级） */
    private Long parentId;

    /** 显示顺序 */
    private Integer sort;

    /** 状态（0=正常 1=停用） */
    private String status;

    public SysCategory()
    {
    }

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    public String getCategoryType()
    {
        return categoryType;
    }

    public void setCategoryType(String categoryType)
    {
        this.categoryType = categoryType;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
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
                .append("categoryId", getCategoryId())
                .append("categoryName", getCategoryName())
                .append("categoryType", getCategoryType())
                .append("parentId", getParentId())
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
