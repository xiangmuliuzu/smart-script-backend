package com.smartscript.platform.review.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/**
 * 任务配置实体（sys_task）
 *
 * @author smartscript
 */
public class TaskConfig {

    private Long taskId;
    private String taskName;
    /** 任务类型 DAILY-每日 READING-阅读 UPLOAD-上传 ACTIVITY-活动 */
    private String taskType;
    /** 任务编码 */
    private String taskCode;
    private String description;
    /** 目标次数 */
    private Integer targetCount;
    /** 奖励积分 */
    private Integer rewardPoints;
    private String icon;
    private Integer sort;
    /** 状态 1-启用 0-停用 */
    private Integer status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }
    public Integer getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Integer rewardPoints) { this.rewardPoints = rewardPoints; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
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
