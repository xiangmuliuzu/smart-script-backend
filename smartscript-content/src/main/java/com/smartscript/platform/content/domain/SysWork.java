package com.smartscript.platform.content.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 作品对象 sys_work
 *
 * 依据：云端 script_platform_dev 库 sys_work 表（附件5.1 表3-14）。
 * 表注释：作品表。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at/updated_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （create_by/create_time/update_by/update_time/remark，继承 BaseEntity）。
 * 2. status 为 varchar(20)（非 tinyint），Entity 用 String 原样持有。
 * 3. trade_enabled/is_free/preview_enabled/is_copyrighted/is_deleted 为 tinyint，
 *    Entity 用 String（若依字典风格 "0"/"1"）。
 * 4. ext_json 为 json 类型，Entity 用 String 持有原文本，mapper xml 透传。
 * 5. authorName/genreName 为 JOIN 查询扩展字段（sys_user.nickname / sys_category.category_name）。
 * 6. recommendStatus/showScope 为书城页从 ext_json 解析的扩展字段（JSON_EXTRACT）。
 * 7. 主键 work_id 为 bigint，Entity 用 Long。
 *
 * @author xiangsipeng
 */
public class SysWork extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 作品ID */
    private Long workId;

    /** 作品标题 */
    private String title;

    /** 封面地址 */
    private String cover;

    /** 作者ID */
    private Long authorId;

    /** 题材ID（关联 sys_category.category_id） */
    private Integer genreId;

    /** 作品类型 */
    private String workType;

    /** 上传类型 */
    private String uploadType;

    /** 篇幅类型 */
    private String lengthType;

    /** 简介 */
    private String summary;

    /** 核心设置 */
    private String coreSetting;

    /** 角色设定 */
    private String characterSetting;

    /** 价格 */
    private BigDecimal price;

    /** 交易类型 */
    private String tradeType;

    /** 是否开启交易（0=否 1=是） */
    private String tradeEnabled;

    /** 议价下限 */
    private BigDecimal negotiableMin;

    /** 议价上限 */
    private BigDecimal negotiableMax;

    /** 报价有效期（天） */
    private Integer quoteValidDays;

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

    /** 状态（varchar(20)：如 draft/pending/approved/on_shelf/off_shelf 等） */
    private String status;

    /** 是否有版权（0=否 1=是） */
    private String isCopyrighted;

    /** 浏览量 */
    private Integer viewCount;

    /** 收藏量 */
    private Integer favoriteCount;

    /** 销量 */
    private Integer saleCount;

    /** 评分 */
    private BigDecimal rating;

    /** 质量等级 */
    private String qualityLevel;

    /** 审核人ID */
    private Long reviewerId;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewTime;

    /** 驳回原因 */
    private String rejectReason;

    /** 扩展JSON（书城推荐+展示范围，原文本透传） */
    private String extJson;

    /** 是否删除（0=否 1=是） */
    private String isDeleted;

    // ---- JOIN 扩展字段 ----

    /** 作者昵称（JOIN sys_user.nickname） */
    private String authorName;

    /** 题材名称（JOIN sys_category.category_name） */
    private String genreName;

    // ---- 书城 ext_json 解析扩展字段 ----

    /** 推荐状态（从 ext_json 解析：home_hot/category_rec/none） */
    private String recommendStatus;

    /** 展示范围（从 ext_json 解析，JSON 数组原文本） */
    private String showScope;

    public SysWork()
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

    public Integer getGenreId()
    {
        return genreId;
    }

    public void setGenreId(Integer genreId)
    {
        this.genreId = genreId;
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

    public BigDecimal getNegotiableMin()
    {
        return negotiableMin;
    }

    public void setNegotiableMin(BigDecimal negotiableMin)
    {
        this.negotiableMin = negotiableMin;
    }

    public BigDecimal getNegotiableMax()
    {
        return negotiableMax;
    }

    public void setNegotiableMax(BigDecimal negotiableMax)
    {
        this.negotiableMax = negotiableMax;
    }

    public Integer getQuoteValidDays()
    {
        return quoteValidDays;
    }

    public void setQuoteValidDays(Integer quoteValidDays)
    {
        this.quoteValidDays = quoteValidDays;
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

    public String getQualityLevel()
    {
        return qualityLevel;
    }

    public void setQualityLevel(String qualityLevel)
    {
        this.qualityLevel = qualityLevel;
    }

    public Long getReviewerId()
    {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId)
    {
        this.reviewerId = reviewerId;
    }

    public Date getReviewTime()
    {
        return reviewTime;
    }

    public void setReviewTime(Date reviewTime)
    {
        this.reviewTime = reviewTime;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }

    public String getExtJson()
    {
        return extJson;
    }

    public void setExtJson(String extJson)
    {
        this.extJson = extJson;
    }

    public String getIsDeleted()
    {
        return isDeleted;
    }

    public void setIsDeleted(String isDeleted)
    {
        this.isDeleted = isDeleted;
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

    public String getRecommendStatus()
    {
        return recommendStatus;
    }

    public void setRecommendStatus(String recommendStatus)
    {
        this.recommendStatus = recommendStatus;
    }

    public String getShowScope()
    {
        return showScope;
    }

    public void setShowScope(String showScope)
    {
        this.showScope = showScope;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("workId", getWorkId())
                .append("title", getTitle())
                .append("authorId", getAuthorId())
                .append("authorName", getAuthorName())
                .append("genreId", getGenreId())
                .append("genreName", getGenreName())
                .append("workType", getWorkType())
                .append("status", getStatus())
                .append("tradeEnabled", getTradeEnabled())
                .append("extJson", getExtJson())
                .append("viewCount", getViewCount())
                .append("wordCount", getWordCount())
                .append("episodeCount", getEpisodeCount())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
