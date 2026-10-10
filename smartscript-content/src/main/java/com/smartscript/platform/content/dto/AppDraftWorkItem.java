package com.smartscript.platform.content.dto;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 草稿箱作品条目（App 接口文档 2.9.5 表 2-115）。
 *
 * 口径：草稿 = sys_work.status='draft' 且 author_id=当前登录用户 且 is_deleted=0。
 * 文档未定义 list 元素字段，这里下发草稿箱页展示所需的最小字段集。
 *
 * @author xiangsipeng
 */
public class AppDraftWorkItem
{
    /** 作品ID */
    private Long workId;

    /** 作品标题 */
    private String title;

    /** 封面地址 */
    private String cover;

    /** 题材ID */
    private Integer genreId;

    /** 题材名称（JOIN sys_category.category_name） */
    private String genreName;

    /** 简介 */
    private String summary;

    /** 价格 */
    private BigDecimal price;

    /** 状态（草稿箱内恒为 draft） */
    private String status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getCover()
    {
        return cover;
    }

    public void setCover(String cover)
    {
        this.cover = cover;
    }

    public Integer getGenreId()
    {
        return genreId;
    }

    public void setGenreId(Integer genreId)
    {
        this.genreId = genreId;
    }

    public String getGenreName()
    {
        return genreName;
    }

    public void setGenreName(String genreName)
    {
        this.genreName = genreName;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}