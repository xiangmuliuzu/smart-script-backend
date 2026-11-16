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
 *   7. props：重要道具档案（名称、件数、外观或能力、当前持有人、是否已交付）——
 *      与 items 的逐集流水不同，这里是全书唯一的一份“道具主数据”，件数与归属以它为准；
 *   8. styleGuide：写法基线（叙述人称、句式与对白比例、标点与禁用写法、单集字数区间）——
 *      首次生成时固化，之后除用户明确要求改文风外不得变化，否则读者会感觉换了写手；
 *   9. threads：未解线索（尚未回收的悬念）。
 *
 * 说明：九类均为自由文本，按“一行一条”的约定由前端维护，后端只存取不解析。
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

    /** 重要道具档案（全书唯一主数据：件数、持有人、是否已交付） */
    private String props;

    /** 写法基线（文风与版式口径，固化后不随意变化） */
    private String styleGuide;

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

    public String getProps()
    {
        return props;
    }

    public void setProps(String props)
    {
        this.props = props;
    }

    public String getStyleGuide()
    {
        return styleGuide;
    }

    public void setStyleGuide(String styleGuide)
    {
        this.styleGuide = styleGuide;
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
