package com.smartscript.platform.content.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 播放历史对象 sys_play_history
 *
 * 依据：云端 script_platform_dev 库 sys_play_history 表。
 * 表注释：用户播放历史表（用户观看剧集的记录，用于运营统计）。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at/updated_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （create_by/create_time/update_by/update_time/remark，继承 BaseEntity）。
 * 2. play_time 为 datetime，Entity 用 Date + @JsonFormat。
 * 3. 主键 id 为 bigint，Entity 用 Long。
 * 4. episodeTitle 为 JOIN 扩展字段（sys_episode.title ON episode_id）。
 *
 * @author xiangsipeng
 */
public class SysPlayHistory extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 剧集ID（关联 sys_episode.episode_id） */
    private Long episodeId;

    /** 作品ID（关联 sys_work.work_id） */
    private Long workId;

    /** 播放时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date playTime;

    // ---- JOIN 扩展字段 ----

    /** 剧集标题（JOIN sys_episode.title ON episode_id） */
    private String episodeTitle;

    public SysPlayHistory()
    {
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getEpisodeId()
    {
        return episodeId;
    }

    public void setEpisodeId(Long episodeId)
    {
        this.episodeId = episodeId;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public Date getPlayTime()
    {
        return playTime;
    }

    public void setPlayTime(Date playTime)
    {
        this.playTime = playTime;
    }

    public String getEpisodeTitle()
    {
        return episodeTitle;
    }

    public void setEpisodeTitle(String episodeTitle)
    {
        this.episodeTitle = episodeTitle;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("userId", getUserId())
                .append("episodeId", getEpisodeId())
                .append("workId", getWorkId())
                .append("playTime", getPlayTime())
                .append("episodeTitle", getEpisodeTitle())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
