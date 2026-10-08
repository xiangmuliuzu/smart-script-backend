package com.smartscript.platform.content.dto;

/** 版权资产列表筛选条件。 */
public class CopyrightAssetQuery
{
    private String keyword;
    private String status;

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
}
