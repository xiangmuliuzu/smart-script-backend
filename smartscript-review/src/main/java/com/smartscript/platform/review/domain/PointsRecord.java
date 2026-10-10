package com.smartscript.platform.review.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/**
 * 积分流水实体（sys_points_record）
 *
 * @author smartscript
 */
public class PointsRecord {

    private Long recordId;
    private Long userId;
    /** 变动类型 EARN-获得 SPEND-消耗 */
    private String changeType;
    /** 变动积分数 */
    private Integer points;
    /** 变动后余额 */
    private Integer balanceAfter;
    /** 来源 TASK-任务 AD-广告 EXCHANGE-兑换 REVERSAL-补偿 */
    private String source;
    private Long relatedId;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    /** 关联用户昵称（联表查询） */
    private String nickname;
    /** 筛选-开始日期 yyyy-MM-dd */
    private String startDate;
    /** 筛选-结束日期 yyyy-MM-dd */
    private String endDate;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public Integer getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Integer balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getRelatedId() { return relatedId; }
    public void setRelatedId(Long relatedId) { this.relatedId = relatedId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
}
