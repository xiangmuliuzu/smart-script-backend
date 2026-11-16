package com.smartscript.platform.content.domain;

import java.util.Date;

/**
 * 作品批量生成任务 sys_work_gen_job
 *
 * 一条任务对应一次「点一次、后台跑完」的提交，按集串行执行：
 *   1. 从库中读最新台账（道具档案/写法基线/流水）与上一集正文末尾，组装承接提示词；
 *   2. 调 AI 写本集正文，去重、控字数后立即落库成章节（不依赖前端保存）；
 *   3. 生成本章摘要并合并台账，下一集才能接到最新状态；
 *   4. 每集结束把 episodes 整条 JSON 回写，进程中断后可按 status 断点续跑。
 *
 * @author smartscript
 */
public class WorkGenJob
{
    /** 排队中 */
    public static final String STATUS_QUEUED = "queued";

    /** 执行中 */
    public static final String STATUS_RUNNING = "running";

    /** 暂停（失败或积分不足，等待续跑） */
    public static final String STATUS_PAUSED = "paused";

    /** 全部完成 */
    public static final String STATUS_SUCCEEDED = "succeeded";

    /** 已取消 */
    public static final String STATUS_CANCELLED = "cancelled";

    private Long jobId;

    private Long workId;

    private Long userId;

    private String status;

    private Integer total;

    private Integer doneCount;

    private Integer currentNo;

    private String currentTitle;

    /** 全剧事件卡 */
    private String outline;

    /** 人物设定（注入用） */
    private String characters;

    /** 人物白名单，顿号分隔 */
    private String roster;

    /** 题材（台账基线起草输入） */
    private String genre;

    /** 用户要求（台账基线起草输入） */
    private String userPrompt;

    /** 本任务使用的模型名 */
    private String model;

    /** 集队列 JSON */
    private String episodes;

    private String lastError;

    private Date createTime;

    private Date updateTime;

    public Long getJobId()
    {
        return jobId;
    }

    public void setJobId(Long jobId)
    {
        this.jobId = jobId;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getTotal()
    {
        return total;
    }

    public void setTotal(Integer total)
    {
        this.total = total;
    }

    public Integer getDoneCount()
    {
        return doneCount;
    }

    public void setDoneCount(Integer doneCount)
    {
        this.doneCount = doneCount;
    }

    public Integer getCurrentNo()
    {
        return currentNo;
    }

    public void setCurrentNo(Integer currentNo)
    {
        this.currentNo = currentNo;
    }

    public String getCurrentTitle()
    {
        return currentTitle;
    }

    public void setCurrentTitle(String currentTitle)
    {
        this.currentTitle = currentTitle;
    }

    public String getOutline()
    {
        return outline;
    }

    public void setOutline(String outline)
    {
        this.outline = outline;
    }

    public String getCharacters()
    {
        return characters;
    }

    public void setCharacters(String characters)
    {
        this.characters = characters;
    }

    public String getRoster()
    {
        return roster;
    }

    public void setRoster(String roster)
    {
        this.roster = roster;
    }

    public String getEpisodes()
    {
        return episodes;
    }

    public void setEpisodes(String episodes)
    {
        this.episodes = episodes;
    }

    public String getGenre()
    {
        return genre;
    }

    public void setGenre(String genre)
    {
        this.genre = genre;
    }

    public String getUserPrompt()
    {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt)
    {
        this.userPrompt = userPrompt;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public String getLastError()
    {
        return lastError;
    }

    public void setLastError(String lastError)
    {
        this.lastError = lastError;
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
