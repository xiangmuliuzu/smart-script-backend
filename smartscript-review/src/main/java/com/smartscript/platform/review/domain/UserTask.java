package com.smartscript.platform.review.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/**
 * 用户任务完成记录实体（sys_user_task）
 *
 * @author smartscript
 */
public class UserTask {

    private Long id;
    private Long userId;
    private Long taskId;
    /** 完成进度 */
    private Integer progress;
    /** 是否已完成 0-否 1-是 */
    private Integer isCompleted;
    /** 是否已领取奖励 0-否 1-是 */
    private Integer isClaimed;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date completeTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date claimTime;
    /** 周期日期（每日任务=当天；一次性任务=首次领取日期） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date periodDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public Integer getIsCompleted() { return isCompleted; }
    public void setIsCompleted(Integer isCompleted) { this.isCompleted = isCompleted; }
    public Integer getIsClaimed() { return isClaimed; }
    public void setIsClaimed(Integer isClaimed) { this.isClaimed = isClaimed; }
    public Date getCompleteTime() { return completeTime; }
    public void setCompleteTime(Date completeTime) { this.completeTime = completeTime; }
    public Date getClaimTime() { return claimTime; }
    public void setClaimTime(Date claimTime) { this.claimTime = claimTime; }
    public Date getPeriodDate() { return periodDate; }
    public void setPeriodDate(Date periodDate) { this.periodDate = periodDate; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
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
