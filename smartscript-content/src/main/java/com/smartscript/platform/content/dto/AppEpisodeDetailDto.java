package com.smartscript.platform.content.dto;

import java.math.BigDecimal;

/**
 * 剧集详情（B 模块 2.8.3 表 2-96）。
 *
 * 依据：云端 script_platform_dev 库 sys_episode 表 + 接口文档表 2-96。
 *
 * 反推处理点：
 * 1. 契约输出 episode_id / title / video_url / unlock_type；此处为播放页需要补齐
 *    coverUrl / duration / episodeNo / workId / isFree / price（均为同一表列，非编造）。
 * 2. is_free 为 tinyint，以字符串 "0"/"1" 下发；unlock_type 可空原样下发。
 * 3. 该接口为公开接口（游客可读），只下发播放所需字段，不含审计字段。
 *
 * @author xiangsipeng
 */
public class AppEpisodeDetailDto
{
    /** 剧集ID */
    private Long episodeId;

    /** 作品ID */
    private Long workId;

    /** 集号 */
    private Integer episodeNo;

    /** 标题 */
    private String title;

    /** 播放地址 */
    private String videoUrl;

    /** 封面地址 */
    private String coverUrl;

    /** 时长（秒） */
    private Integer duration;

    /** 是否免费（"0"=否 "1"=是） */
    private String isFree;

    /** 解锁类型（可空） */
    private String unlockType;

    /** 价格（可空） */
    private BigDecimal price;

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
}