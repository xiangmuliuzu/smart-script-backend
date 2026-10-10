package com.smartscript.platform.content.dto;

import java.math.BigDecimal;

/**
 * 更新作品请求（App 接口文档 2.9.3 表 2-113）。
 *
 * 文档只定义 title / description / price 三项可改（无 category_id / cover），
 * 故本批不扩展编辑字段；description 落库 sys_work.summary。
 *
 * @author xiangsipeng
 */
public class AppWorkUpdateRequest
{
    /** 作品标题（可选，为空不更新） */
    private String title;

    /** 作品简介（可选，为空不更新，落库 summary） */
    private String description;

    /** 价格（可选，为 null 不更新） */
    private BigDecimal price;

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }
}