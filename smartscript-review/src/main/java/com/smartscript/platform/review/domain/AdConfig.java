package com.smartscript.platform.review.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/**
 * 广告配置实体（sys_ad_config）
 *
 * @author smartscript
 */
public class AdConfig {

    private Long adId;
    private String adName;
    private String adPosition;
    private String adType;
    private String adSource;
    private String adUnitId;
    /** 是否启用 1-启用 0-停用 */
    private Integer isEnabled;
    /** 频次限制/天 */
    private Integer frequencyLimit;
    /** 每日上限 */
    private Integer dailyCap;
    /** 人群定向 JSON */
    private String targetAudience;
    /** 观看上限 */
    private Integer watchLimit;
    /** 观看奖励积分 */
    private Integer rewardPoints;
    private Integer sort;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date startDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date endDate;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

    public Long getAdId() { return adId; }
    public void setAdId(Long adId) { this.adId = adId; }
    public String getAdName() { return adName; }
    public void setAdName(String adName) { this.adName = adName; }
    public String getAdPosition() { return adPosition; }
    public void setAdPosition(String adPosition) { this.adPosition = adPosition; }
    public String getAdType() { return adType; }
    public void setAdType(String adType) { this.adType = adType; }
    public String getAdSource() { return adSource; }
    public void setAdSource(String adSource) { this.adSource = adSource; }
    public String getAdUnitId() { return adUnitId; }
    public void setAdUnitId(String adUnitId) { this.adUnitId = adUnitId; }
    public Integer getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Integer isEnabled) { this.isEnabled = isEnabled; }
    public Integer getFrequencyLimit() { return frequencyLimit; }
    public void setFrequencyLimit(Integer frequencyLimit) { this.frequencyLimit = frequencyLimit; }
    public Integer getDailyCap() { return dailyCap; }
    public void setDailyCap(Integer dailyCap) { this.dailyCap = dailyCap; }
    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }
    public Integer getWatchLimit() { return watchLimit; }
    public void setWatchLimit(Integer watchLimit) { this.watchLimit = watchLimit; }
    public Integer getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Integer rewardPoints) { this.rewardPoints = rewardPoints; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }
    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
