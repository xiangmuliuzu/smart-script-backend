package com.smartscript.platform.content.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * App 分类树节点（三级：频道 → 题材 → 细分）。
 *
 * 依据：云端 script_platform_dev 库 sys_category + 三级分类迁移
 *       B_20261029_001__category_three_levels.sql。
 *
 * 一次请求下发全量三级树，避免 App 逐层多次请求；只包含 status 正常（'0'）的分类，
 * 且父子均按 sort 升序排列。
 *
 * @author xiangsipeng
 */
public class AppCategoryNodeDto
{
    /** 分类ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 分类类型（channel/theme/work 等） */
    private String categoryType;

    /** 父级ID（0=顶级） */
    private Long parentId;

    /** 显示顺序 */
    private Integer sort;

    /** 子分类（无子级时为空集合，不下发 null） */
    private List<AppCategoryNodeDto> children = new ArrayList<>();

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

    public List<AppCategoryNodeDto> getChildren()
    {
        return children;
    }

    public void setChildren(List<AppCategoryNodeDto> children)
    {
        this.children = children;
    }
}
