package com.smartscript.platform.content.dto;

import java.util.List;

/**
 * 作品榜单返回体（App 接口文档 2.7.7 的 data.list / PC 排行榜实时查询共用）。
 *
 * 字段：
 *   list      榜单条目，rankNo 从 1 起递增
 *   updatedAt 榜单更新时间（实时查询的取数时刻，格式 yyyy-MM-dd HH:mm:ss），
 *             对应 PRD 5.2「榜单数据按规则排序并标明更新时间」
 *
 * @author xiangsipeng
 */
public class AppRankingResult
{
    /** 榜单条目 */
    private List<AppRankingItem> list;

    /** 榜单更新时间（yyyy-MM-dd HH:mm:ss） */
    private String updatedAt;

    public List<AppRankingItem> getList()
    {
        return list;
    }

    public void setList(List<AppRankingItem> list)
    {
        this.list = list;
    }

    public String getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt)
    {
        this.updatedAt = updatedAt;
    }
}
