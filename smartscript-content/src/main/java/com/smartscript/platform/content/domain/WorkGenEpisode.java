package com.smartscript.platform.content.domain;

/**
 * 队列中的一集（以 JSON 形式存于 sys_work_gen_job.episodes）。
 *
 * @author smartscript
 */
public class WorkGenEpisode
{
    /** 待生成 */
    public static final String STATUS_PENDING = "pending";

    /** 生成中 */
    public static final String STATUS_RUNNING = "running";

    /** 已完成 */
    public static final String STATUS_SUCCEEDED = "succeeded";

    /** 失败（重试用尽，等待续跑） */
    public static final String STATUS_FAILED = "failed";

    /** 集号 */
    private Integer no;

    /** 集名 */
    private String title;

    /** 本集事件卡 */
    private String card;

    private String status;

    /** 落库后的章节ID */
    private Long chapterId;

    /** 本集失败原因 */
    private String error;

    /** 已尝试次数 */
    private Integer attempts;

    public Integer getNo()
    {
        return no;
    }

    public void setNo(Integer no)
    {
        this.no = no;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getCard()
    {
        return card;
    }

    public void setCard(String card)
    {
        this.card = card;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getChapterId()
    {
        return chapterId;
    }

    public void setChapterId(Long chapterId)
    {
        this.chapterId = chapterId;
    }

    public String getError()
    {
        return error;
    }

    public void setError(String error)
    {
        this.error = error;
    }

    public Integer getAttempts()
    {
        return attempts;
    }

    public void setAttempts(Integer attempts)
    {
        this.attempts = attempts;
    }
}
