package com.smartscript.platform.content.dto;

import java.util.Date;

/**
 * 播放历史列表项（B 模块 2.8.6 表 2-99）。
 *
 * 依据：云端 script_platform_dev 库 sys_play_history + sys_episode + sys_work +
 * sys_play_progress 表 + 接口文档表 2-99。
 *
 * 反推处理点：
 * 1. 契约仅定义 data={total,list}，未定义元素字段；此处下发「播放历史页」所需的
 *    作品/剧集展示信息与续播位置（均为既有表列，非编造）。
 * 2. 续播位置来自 sys_play_progress（按 user_id + episode_id），无进度记录时为空；
 *    播放历史行本身不含进度列（见 schema）。
 * 3. playTime 为 sys_play_history.play_time。
 *
 * @author xiangsipeng
 */
public class AppPlayHistoryItem
{
    /** 播放历史ID */
    private Integer id;

    /** 作品ID */
    private Long workId;

    /** 作品标题（JOIN sys_work.title） */
    private String workTitle;

    /** 剧集ID */
    private Long episodeId;

    /** 剧集标题（JOIN sys_episode.title） */
    private String episodeTitle;

    /** 剧集封面（JOIN sys_episode.cover_url） */
    private String coverUrl;

    /** 集号（JOIN sys_episode.episode_no） */
    private Integer episodeNo;

    /** 播放时间 */
    private Date playTime;

    /** 已播放秒数（续播位置，可空） */
    private Integer progressSeconds;

    /** 总时长（秒，可空） */
    private Integer totalDuration;

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public String getWorkTitle()
    {
        return workTitle;
    }

    public void setWorkTitle(String workTitle)
    {
        this.workTitle = workTitle;
    }

    public Long getEpisodeId()
    {
        return episodeId;
    }

    public void setEpisodeId(Long episodeId)
    {
        this.episodeId = episodeId;
    }

    public String getEpisodeTitle()
    {
        return episodeTitle;
    }

    public void setEpisodeTitle(String episodeTitle)
    {
        this.episodeTitle = episodeTitle;
    }

    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    public Integer getEpisodeNo()
    {
        return episodeNo;
    }

    public void setEpisodeNo(Integer episodeNo)
    {
        this.episodeNo = episodeNo;
    }

    public Date getPlayTime()
    {
        return playTime;
    }

    public void setPlayTime(Date playTime)
    {
        this.playTime = playTime;
    }

    public Integer getProgressSeconds()
    {
        return progressSeconds;
    }

    public void setProgressSeconds(Integer progressSeconds)
    {
        this.progressSeconds = progressSeconds;
    }

    public Integer getTotalDuration()
    {
        return totalDuration;
    }

    public void setTotalDuration(Integer totalDuration)
    {
        this.totalDuration = totalDuration;
    }
}