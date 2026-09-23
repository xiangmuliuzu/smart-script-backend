package com.smartscript.platform.trade.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * C module: work entity (sys_work).
 * Represents a creative work (script) available for trade.
 * This is a read-heavy table; C module mainly queries trade-relevant fields.
 */
public class SysWork extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long workId;
    private String title;
    private String cover;
    private Long authorId;
    private Integer genreId;
    private String workType;
    private String uploadType;
    private String lengthType;
    private String summary;
    private String coreSetting;
    private String characterSetting;
    private BigDecimal price;
    private String tradeType;
    private Integer tradeEnabled;
    private BigDecimal negotiableMin;
    private BigDecimal negotiableMax;
    private Integer quoteValidDays;
    private Integer wordCount;
    private Integer episodeCount;
    private Integer duration;
    private Integer isFree;
    private Integer previewEnabled;
    private Integer previewEpisodes;
    private String status;
    private Integer isCopyrighted;
    private Integer viewCount;
    private Integer favoriteCount;
    private Integer saleCount;
    private BigDecimal rating;
    private String qualityLevel;
    private Long reviewerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewTime;

    private String rejectReason;
    private String extJson;
    private Integer isDeleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** Transient: author name joined from sys_user */
    private String authorName;

    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Integer getGenreId() { return genreId; }
    public void setGenreId(Integer genreId) { this.genreId = genreId; }
    public String getWorkType() { return workType; }
    public void setWorkType(String workType) { this.workType = workType; }
    public String getUploadType() { return uploadType; }
    public void setUploadType(String uploadType) { this.uploadType = uploadType; }
    public String getLengthType() { return lengthType; }
    public void setLengthType(String lengthType) { this.lengthType = lengthType; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCoreSetting() { return coreSetting; }
    public void setCoreSetting(String coreSetting) { this.coreSetting = coreSetting; }
    public String getCharacterSetting() { return characterSetting; }
    public void setCharacterSetting(String characterSetting) { this.characterSetting = characterSetting; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getTradeType() { return tradeType; }
    public void setTradeType(String tradeType) { this.tradeType = tradeType; }
    public Integer getTradeEnabled() { return tradeEnabled; }
    public void setTradeEnabled(Integer tradeEnabled) { this.tradeEnabled = tradeEnabled; }
    public BigDecimal getNegotiableMin() { return negotiableMin; }
    public void setNegotiableMin(BigDecimal negotiableMin) { this.negotiableMin = negotiableMin; }
    public BigDecimal getNegotiableMax() { return negotiableMax; }
    public void setNegotiableMax(BigDecimal negotiableMax) { this.negotiableMax = negotiableMax; }
    public Integer getQuoteValidDays() { return quoteValidDays; }
    public void setQuoteValidDays(Integer quoteValidDays) { this.quoteValidDays = quoteValidDays; }
    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }
    public Integer getEpisodeCount() { return episodeCount; }
    public void setEpisodeCount(Integer episodeCount) { this.episodeCount = episodeCount; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Integer getIsFree() { return isFree; }
    public void setIsFree(Integer isFree) { this.isFree = isFree; }
    public Integer getPreviewEnabled() { return previewEnabled; }
    public void setPreviewEnabled(Integer previewEnabled) { this.previewEnabled = previewEnabled; }
    public Integer getPreviewEpisodes() { return previewEpisodes; }
    public void setPreviewEpisodes(Integer previewEpisodes) { this.previewEpisodes = previewEpisodes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getIsCopyrighted() { return isCopyrighted; }
    public void setIsCopyrighted(Integer isCopyrighted) { this.isCopyrighted = isCopyrighted; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Integer favoriteCount) { this.favoriteCount = favoriteCount; }
    public Integer getSaleCount() { return saleCount; }
    public void setSaleCount(Integer saleCount) { this.saleCount = saleCount; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public String getQualityLevel() { return qualityLevel; }
    public void setQualityLevel(String qualityLevel) { this.qualityLevel = qualityLevel; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Date getReviewTime() { return reviewTime; }
    public void setReviewTime(Date reviewTime) { this.reviewTime = reviewTime; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getExtJson() { return extJson; }
    public void setExtJson(String extJson) { this.extJson = extJson; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
}
