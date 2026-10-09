package com.smartscript.platform.content.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 书城作品（App 侧只读下发对象）。
 *
 * 依据：云端 script_platform_dev 库 sys_work 表（附件5.1 表3-14）。
 * 字段裁剪：只下发 App 展示所需字段，不下发内部字段
 * （ext_json / reject_reason / reviewer_id / review_time / is_deleted / 若依审计字段）。
 *
 * 列表与详情共用本对象：
 *   - 列表不查询 core_setting/character_setting（长文本），这两项在列表中为 null；
 *   - 详情额外下发 core_setting/character_setting。
 *
 * @author xiangsipeng
 */
public class AppWorkDto
{
    /** 作品ID */
    private Long workId;

    /** 作品标题 */
    private String title;

    /** 封面地址 */
    private String cover;

    /** 作者ID */
    private Long authorId;

    /** 作者昵称（JOIN sys_user.nick_name） */
    private String authorName;

    /** 题材ID */
    private Integer genreId;

    /** 题材名称（JOIN sys_category.category_name） */
    private String genreName;

    /** 标签集合（sys_work_tag 关联 sys_tag，仅含启用标签，按 sort 排序） */
    private List<AppTagDto> tags;

    /** 作品类型 */
    private String workType;

    /** 上传类型 */
    private String uploadType;

    /** 篇幅类型 */
    private String lengthType;

    /** 简介 */
    private String summary;

    /** 核心设置（仅详情下发） */
    private String coreSetting;

    /** 角色设定（仅详情下发） */
    private String characterSetting;

    /** 价格 */
    private BigDecimal price;

    /** 交易类型 */
    private String tradeType;

    /** 是否开启交易（0=否 1=是） */
    private String tradeEnabled;

    /** 字数 */
    private Integer wordCount;

    /** 集数 */
    private Integer episodeCount;

    /** 时长 */
    private Integer duration;

    /** 是否免费（0=否 1=是） */
    private String isFree;

    /** 是否开启预览（0=否 1=是） */
    private String previewEnabled;

    /** 预览集数 */
    private Integer previewEpisodes;

    /** 状态（书城只下发 on_shelf） */
    private String status;

    /** 是否有版权（0=否 1=是） */
    private String isCopyrighted;

    /** 质量等级 */
    private String qualityLevel;

    /** 浏览量 */
    private Integer viewCount;

    /** 收藏量 */
    private Integer favoriteCount;

    /** 销量 */
    private Integer saleCount;

    /** 评分 */
    private BigDecimal rating;

    /** 上架时间（sys_work.create_time） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public AppWorkDto()
    {
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

    public Long getAuthorId()
    {
        return authorId;
    }

    public void setAuthorId(Long authorId)
    {
        this.authorId = authorId;
    }

    public String getAuthorName()
    {
        return authorName;
    }

    public void setAuthorName(String authorName)
    {
        this.authorName = authorName;
    }

    public Integer getGenreId()
    {
        return genreId;
    }

    public void setGenreId(Integer genreId)
    {
        this.genreId = genreId;
    }

    public String getGenreName()
    {
        return genreName;
    }

    public void setGenreName(String genreName)
    {
        this.genreName = genreName;
    }

    public List<AppTagDto> getTags()
    {
        return tags;
    }

    public void setTags(List<AppTagDto> tags)
    {
        this.tags = tags;
    }

    public String getWorkType()
    {
        return workType;
    }

    public void setWorkType(String workType)
    {
        this.workType = workType;
    }

    public String getUploadType()
    {
        return uploadType;
    }

    public void setUploadType(String uploadType)
    {
        this.uploadType = uploadType;
    }

    public String getLengthType()
    {
        return lengthType;
    }

    public void setLengthType(String lengthType)
    {
        this.lengthType = lengthType;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getCoreSetting()
    {
        return coreSetting;
    }

    public void setCoreSetting(String coreSetting)
    {
        this.coreSetting = coreSetting;
    }

    public String getCharacterSetting()
    {
        return characterSetting;
    }

    public void setCharacterSetting(String characterSetting)
    {
        this.characterSetting = characterSetting;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public String getTradeType()
    {
        return tradeType;
    }

    public void setTradeType(String tradeType)
    {
        this.tradeType = tradeType;
    }

    public String getTradeEnabled()
    {
        return tradeEnabled;
    }

    public void setTradeEnabled(String tradeEnabled)
    {
        this.tradeEnabled = tradeEnabled;
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

    public String getPreviewEnabled()
    {
        return previewEnabled;
    }

    public void setPreviewEnabled(String previewEnabled)
    {
        this.previewEnabled = previewEnabled;
    }

    public Integer getPreviewEpisodes()
    {
        return previewEpisodes;
    }

    public void setPreviewEpisodes(Integer previewEpisodes)
    {
        this.previewEpisodes = previewEpisodes;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getIsCopyrighted()
    {
        return isCopyrighted;
    }

    public void setIsCopyrighted(String isCopyrighted)
    {
        this.isCopyrighted = isCopyrighted;
    }

    public String getQualityLevel()
    {
        return qualityLevel;
    }

    public void setQualityLevel(String qualityLevel)
    {
        this.qualityLevel = qualityLevel;
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

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}