package com.smartscript.platform.content.dto;

import java.util.List;

/**
 * 搜索历史列表载荷（接口文档 2.7.4 表2-84）。
 *
 * 契约输出仅 data.list，无 total，故本对象只承载 list，
 * 列表按 lastSearchAt 倒序取最近若干条（不做分页）。
 *
 * @author xiangsipeng
 */
public class AppSearchHistoryListDto
{
    /** 搜索历史列表（按最近搜索时间倒序） */
    private List<AppSearchHistoryItem> list;

    public AppSearchHistoryListDto()
    {
    }

    public AppSearchHistoryListDto(List<AppSearchHistoryItem> list)
    {
        this.list = list;
    }

    public List<AppSearchHistoryItem> getList()
    {
        return list;
    }

    public void setList(List<AppSearchHistoryItem> list)
    {
        this.list = list;
    }
}