package com.smartscript.platform.content.dto;

import java.math.BigDecimal;

/**
 * 书城榜单条目（App 侧只读，接口文档 2.7.7 表 2-87「作品排行榜（4种排序）」）。
 *
 * 数据来源：sys_work 真实指标列，不依赖 sys_ranking_snapshot 快照是否已重算。
 * type 与指标列对应关系（score 即对应指标）：
 *   view     浏览量     sys_work.view_count
 *   favorite 收藏量     sys_work.favorite_count
 *   sale     销量       sys_work.sale_count
 *   rating   评分       sys_work.rating
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

    /** 榜单分数（等于 type 对应的指标值） */
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