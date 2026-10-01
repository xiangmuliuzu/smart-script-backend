package com.smartscript.platform.content.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 剧集对象 sys_episode
 *
 * 依据：云端 script_platform_dev 库 sys_episode 表。
 * 表注释：短剧剧集表（一个作品 work_id 下多集短剧）。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at/updated_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （create_by/create_time/update_by/update_time/remark，继承 BaseEntity）。
 * 2. is_free/status 为 tinyint，Entity 用 String（若依字典风格 "0"/"1"）。
 * 3. unlock_type 为 varchar(20) 可空，String 原样持有。
 * 4. price/completion_rate 为 decimal，Entity 用 BigDecimal。
 * 5. 主键 episode_id 为 bigint，Entity 用 Long。
 *
 * @author xiangsipeng
 */
public class SysEpisode extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 剧集ID */
    private Long episodeId;

    /** 作品ID（关联 sys_work.work_id） */
    private Long workId;

    /** 集号 */
    private Integer episodeNo;

    /** 标题 */
    private String title;

    /** 视频地址 */
    private String videoUrl;

    /** 封面地址 */
    private String coverUrl;

    /** 时长（秒） */
    private Integer duration;

    /** 是否免费（0=否 1=是） */
    private String isFree;

    /** 解锁类型（可空） */
    private String unlockType;

    /** 价格 */
    private BigDecimal price;

    /** 播放数 */
    private Integer playCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 完播率 */
    private BigDecimal completionRate;

    /** 状态（0=正常 1=停用） */
    private String status;

    public SysEpisode()
    {
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

    public Integer getEpisodeNo()
    {
        return episodeNo;
    }

    public void setEpisodeNo(Integer episodeNo)
    {
        this.episodeNo = episodeNo;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getVideoUrl()
    {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl)
    {
        this.videoUrl = videoUrl;
    }

    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    public Integer getDuration()
    {
        return duration;
    }

    public void setDuration(Integer duration)
    {
        this.duration = duration;
    }

    public String getIsFree()
    {
        return isFree;
    }

    public void setIsFree(String isFree)
    {
        this.isFree = isFree;
    }

    public String getUnlockType()
    {
        return unlockType;
    }

    public void setUnlockType(String unlockType)
    {
        this.unlockType = unlockType;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public Integer getPlayCount()
    {
        return playCount;
    }

    public void setPlayCount(Integer playCount)
    {
        this.playCount = playCount;
    }

    public Integer getLikeCount()
    {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount)
    {
        this.likeCount = likeCount;
    }

    public Integer getCommentCount()
    {
        return commentCount;
    }

    public void setCommentCount(Integer commentCount)
    {
        this.commentCount = commentCount;
    }

    public BigDecimal getCompletionRate()
    {
        return completionRate;
    }

    public void setCompletionRate(BigDecimal completionRate)
    {
        this.completionRate = completionRate;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("episodeId", getEpisodeId())
                .append("workId", getWorkId())
                .append("episodeNo", getEpisodeNo())
                .append("title", getTitle())
                .append("videoUrl", getVideoUrl())
                .append("duration", getDuration())
                .append("isFree", getIsFree())
                .append("playCount", getPlayCount())
                .append("status", getStatus())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
