package com.smartscript.platform.content.dto;

import java.math.BigDecimal;

/**
 * 剧集列表项（B 模块 2.8.2 表 2-95）。
 *
 * 依据：云端 script_platform_dev 库 sys_episode 表 + 接口文档表 2-95。
 *
 * 反推处理点：
 * 1. 契约仅定义 data={list}，未定义元素字段；此处下发目录展示所需子集（集号/标题/封面/时长/解锁态）。
 * 2. is_free 为 tinyint，沿用 B 模块 App 契约以字符串 "0"/"1" 下发（与 AppWorkDto/AppChapterDto 一致）。
 * 3. unlock_type/price 仅在付费集有值，原样下发供客户端展示解锁方式；本批不做解锁接口（2.8.7~2.8.9 归他批）。
 * 4. status 不参与过滤与下发：该列在库端无权威枚举且无写入方，既有 PC 侧
 *    SysEpisodeMapper.selectEpisodeListByWorkId 同样不按 status 过滤，本批沿用同一口径。
 *
 * @author xiangsipeng
 */
public class AppEpisodeItem
{
    /** 剧集ID */
    private Long episodeId;

    /** 作品ID */
    private Long workId;

    /** 集号 */
    private Integer episodeNo;

    /** 标题 */
    private String title;

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

    /** 播放数 */
    private Integer playCount;

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
}