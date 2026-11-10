package com.smartscript.platform.content.domain;

/**
 * 作品创作台账对象 sys_work_ledger
 *
 * 一作品一条，跨端（PC 用户端 / App）共用，用于解决大纲与正文不连续：
 *   1. storySummary：故事简介，写大纲/正文时作为全局背景；
 *   2. characters：主要人物卡（姓名 / 身份 / 目标 / 当前状态）；
 *   3. relations：人物关系当前状态（含上次变化原因）；
 *   4. foreshadow：伏笔台账（埋在哪章 / 计划回收章 / 是否已回收）；
 *   5. timeline：时间线（关键事件发生的时间与地点）；
 *   6. items：物品位置（关键道具当前在谁手上 / 在哪）；
 *   7. threads：未解线索（尚未回收的悬念）。
 *
 * 说明：七类均为自由文本，按“一行一条”的约定由前端维护，后端只存取不解析。
 *
 * @author smartscript
 */
public class SysWorkLedger
{
    /** 作品ID（主键） */
    private Long workId;

    /** 故事简介 */
    private String storySummary;

    /** 主要人物卡 */
    private String characters;

    /** 人物关系当前状态 */
    private String relations;

    /** 伏笔台账 */
    private String foreshadow;

    /** 时间线 */
    private String timeline;

    /** 物品位置 */
    private String items;

    /** 未解线索 */
    private String threads;

    /** 最后更新时间（只读下发） */
    private String updateTime;

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public String getStorySummary()
    {
        return storySummary;
    }

    public void setStorySummary(String storySummary)
    {
        this.storySummary = storySummary;
    }

    public String getCharacters()
    {
        return characters;
    }

    public void setCharacters(String characters)
    {
        this.characters = characters;
    }

    public String getRelations()
    {
        return relations;
    }

    public void setRelations(String relations)
    {
        this.relations = relations;
    }

    public String getForeshadow()
    {
        return foreshadow;
    }

    public void setForeshadow(String foreshadow)
    {
        this.foreshadow = foreshadow;
    }

    public String getTimeline()
    {
        return timeline;
    }

    public void setTimeline(String timeline)
    {
        this.timeline = timeline;
    }

    public String getItems()
    {
        return items;
    }

    public void setItems(String items)
    {
        this.items = items;
    }

    public String getThreads()
    {
        return threads;
    }

    public void setThreads(String threads)
    {
        this.threads = threads;
    }

    public String getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(String updateTime)
    {
        this.updateTime = updateTime;
    }
}
