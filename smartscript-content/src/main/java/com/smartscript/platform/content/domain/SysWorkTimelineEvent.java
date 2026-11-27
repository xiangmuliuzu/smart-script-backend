package com.smartscript.platform.content.domain;

import java.util.Date;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 作品时间线事件对象 sys_work_timeline_event。
 *
 * 只追加：每集写完由摘要调用一并产出 0~3 条重要事件，按 chapter_no 排序；
 * 整集重写时仅允许删掉该集原有事件后按新正文重写，历史集事件不可改。
 */
public class SysWorkTimelineEvent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 未兑现（续写优先检索） */
    public static final String STATUS_OPEN = "未兑现";

    /** 已兑现 */
    public static final String STATUS_DONE = "已兑现";

    /** 已失效（条件不再成立，无需再填） */
    public static final String STATUS_VOID = "已失效";

    private Long eventId;

    private Long workId;

    /** 埋在哪一集（排序主键） */
    private Integer chapterNo;

    /** 同集内序号，从 1 开始 */
    private Integer seq;

    /** 约定/伏笔/道具转移/身份变化/关系变化/生死退场/能力突破/信息揭示/期限/其他 */
    private String eventType;

    /** 一句话事实：谁 + 做了什么 + 条件或代价 */
    private String fact;

    /** 涉及人物，顿号分隔 */
    private String actors;

    /** 涉及道具或线索，顿号分隔 */
    private String props;

    /** 未兑现/已兑现/已失效 */
    private String status;

    /** 计划兑现集号，可为空 */
    private Integer deadlineNo;

    /** 故事内时间（自由文本，如「七日后大比当日」），不参与排序 */
    private String storyTime;

    private Date createTime;

    private Date updateTime;

    public Long getEventId()
    {
        return eventId;
    }

    public void setEventId(Long eventId)
    {
        this.eventId = eventId;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public Integer getChapterNo()
    {
        return chapterNo;
    }

    public void setChapterNo(Integer chapterNo)
    {
        this.chapterNo = chapterNo;
    }

    public Integer getSeq()
    {
        return seq;
    }

    public void setSeq(Integer seq)
    {
        this.seq = seq;
    }

    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
    }

    public String getFact()
    {
        return fact;
    }

    public void setFact(String fact)
    {
        this.fact = fact;
    }

    public String getActors()
    {
        return actors;
    }

    public void setActors(String actors)
    {
        this.actors = actors;
    }

    public String getProps()
    {
        return props;
    }

    public void setProps(String props)
    {
        this.props = props;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getDeadlineNo()
    {
        return deadlineNo;
    }

    public void setDeadlineNo(Integer deadlineNo)
    {
        this.deadlineNo = deadlineNo;
    }

    public String getStoryTime()
    {
        return storyTime;
    }

    public void setStoryTime(String storyTime)
    {
        this.storyTime = storyTime;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
