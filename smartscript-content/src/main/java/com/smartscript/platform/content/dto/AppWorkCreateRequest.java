package com.smartscript.platform.content.dto;

import java.math.BigDecimal;

/**
 * 创建作品请求（App 接口文档 2.9.2 表 2-112）。
 *
 * 入参：title 必填；categoryId 落库 sys_work.genre_id（该列 NOT NULL，故本模块约定必填，
 * 缺失按 400 拒绝，不编造默认分类）；description 落库 sys_work.summary；cover / price 可选。
 *
 * @author xiangsipeng
 */
public class AppWorkCreateRequest
{
    /** 作品标题（必填） */
    private String title;

    /** 分类ID（必填，落库 genre_id） */
    private Integer categoryId;

    /** 作品简介（可选，落库 summary） */
    private String description;

    /** 封面地址（可选） */
    private String cover;

    /** 价格（可选，缺省 0.00） */
    private BigDecimal price;

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Integer getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId)
    {
        this.categoryId = categoryId;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getCover()
    {
        return cover;
    }

    public void setCover(String cover)
    {
        this.cover = cover;
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