package com.smartscript.platform.content.dto;

import java.math.BigDecimal;

/**
 * 书城榜单条目（App 侧只读，接口文档 2.7.7 表 2-87「作品排行榜」）。
 *
 * 数据来源：sys_work 指标列 + 互动表时间窗口实时聚合，不依赖 sys_ranking_snapshot 快照是否已重算。
 * type 与主指标的对应关系（score 即该榜主指标取值）：
 *   view     热门榜      最近 7 天阅读量（sys_read_record 计数；无数据回退累计 view_count）
 *   favorite 收藏榜      最近 7 天收藏数（sys_favorite 计数）
 *   sale     交易热度榜  最近 7 天已支付订单数（sys_order 计数）
 *   rating   评分榜      贝叶斯加权评分 (C*m + sum)/(C + n)
 *   new      新书榜      最近 30 天上架，按累计阅读量排序（score = view_count）
 * 其余 viewCount/favoriteCount/saleCount/rating 均为作品累计指标原值，供前端展示用。
 *
 * @author xiangsipeng
 */
public class AppRankingItem
{
    /** 排名（1 起，服务层按结果顺序生成） */
    private Integer rankNo;

    /** 作品ID */
    private Long workId;

    /** 作品标题 */
    private String title;

    /** 封面地址 */
    private String cover;

    /** 作者昵称（JOIN sys_user.nick_name） */
    private String authorName;

    /** 题材名称（JOIN sys_category.category_name） */
    private String genreName;

    /** 榜单分数 = 该榜主指标的取值（由 SQL 的 rank_score 列映射） */
    private BigDecimal score;

    /** 浏览量 */
    private Integer viewCount;

    /** 收藏量 */
    private Integer favoriteCount;

    /** 销量 */
    private Integer saleCount;

    /** 评分 */
    private BigDecimal rating;

    /** 字数 */
    private Integer wordCount;

    /** 集数 */
    private Integer episodeCount;

    /** 价格 */
    private BigDecimal price;

    /** 是否免费（0=否 1=是） */
    private String isFree;

    public Integer getRankNo()
    {
        return rankNo;
    }

    public void setRankNo(Integer rankNo)
    {
        this.rankNo = rankNo;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getCover()
    {
        return cover;
    }

    public void setCover(String cover)
    {
        this.cover = cover;
    }

    public String getAuthorName()
    {
        return authorName;
    }

    public void setAuthorName(String authorName)
    {
        this.authorName = authorName;
    }

    public String getGenreName()
    {
        return genreName;
    }

    public void setGenreName(String genreName)
    {
        this.genreName = genreName;
    }

    public BigDecimal getScore()
    {
        return score;
    }

    public void setScore(BigDecimal score)
    {
        this.score = score;
    }

    public Integer getViewCount()
    {
        return viewCount;
    }

    public void setViewCount(Integer viewCount)
    {
        this.viewCount = viewCount;
    }

    public Integer getFavoriteCount()
    {
        return favoriteCount;
    }

    public void setFavoriteCount(Integer favoriteCount)
    {
        this.favoriteCount = favoriteCount;
    }

    public Integer getSaleCount()
    {
        return saleCount;
    }

    public void setSaleCount(Integer saleCount)
    {
        this.saleCount = saleCount;
    }

    public BigDecimal getRating()
    {
        return rating;
    }

    public void setRating(BigDecimal rating)
    {
        this.rating = rating;
    }

    public Integer getWordCount()
    {
        return wordCount;
    }

    public void setWordCount(Integer wordCount)
    {
        this.wordCount = wordCount;
    }

    public Integer getEpisodeCount()
    {
        return episodeCount;
    }

    public void setEpisodeCount(Integer episodeCount)
    {
        this.episodeCount = episodeCount;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public String getIsFree()
    {
        return isFree;
    }

    public void setIsFree(String isFree)
    {
        this.isFree = isFree;
    }
}