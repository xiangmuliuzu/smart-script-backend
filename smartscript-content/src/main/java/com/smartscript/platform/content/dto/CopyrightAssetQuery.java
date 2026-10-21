package com.smartscript.platform.content.dto;

/** 版权资产列表筛选条件。 */
public class CopyrightAssetQuery
{
    private String keyword;
    private String status;
    /** 创作者侧：按作者过滤；Admin 不传则查全平台。 */
    private Long authorId;
    /** 创作者侧：仅已发布（最新审核 approved 且 status 为上架/下架）；Admin 不传。 */
    private Boolean onlyPublished;

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getAuthorId()
    {
        return authorId;
    }

    public void setAuthorId(Long authorId)
    {
        this.authorId = authorId;
    }

    public Boolean getOnlyPublished()
    {
        return onlyPublished;
    }

    public void setOnlyPublished(Boolean onlyPublished)
    {
        this.onlyPublished = onlyPublished;
    }
}
