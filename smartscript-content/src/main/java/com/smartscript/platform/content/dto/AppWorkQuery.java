package com.smartscript.platform.content.dto;

/**
 * 书城作品列表查询条件（App 侧只读，接口文档 2.7.1 表 2-81）。
 *
 * 依据：接口文档入参 category_id / tag_id / keyword / sort；
 * 排序取值（sort）与分页（page/pageSize）由控制层收敛，不进入本对象。
 *
 * @author xiangsipeng
 */
public class AppWorkQuery
{
    /** 关键词（按 title 模糊） */
    private String keyword;

    /** 分类ID（sys_work.genre_id） */
    private Integer categoryId;

    /** 标签ID（sys_work_tag.tag_id） */
    private Long tagId;

    /** 排序方式：latest/view/favorite/sale/rating/price_asc/price_desc */
    private String sort;

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public Integer getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId)
    {
        this.categoryId = categoryId;
    }

    public Long getTagId()
    {
        return tagId;
    }

    public void setTagId(Long tagId)
    {
        this.tagId = tagId;
    }

    public String getSort()
    {
        return sort;
    }

    public void setSort(String sort)
    {
        this.sort = sort;
    }
}