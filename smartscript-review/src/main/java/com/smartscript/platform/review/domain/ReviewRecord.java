package com.smartscript.platform.review.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/**
 * 审核记录实体
 *
 * @author smartscript
 */
public class ReviewRecord {

    private Long reviewId;
    private String targetType;
    private Long targetId;
    private String status;
    private String aiResult;
    private String reviewOpinion;
    private Long reviewerId;
    private Date reviewedAt;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

    // 扩展字段
    private String workTitle;
    private String workType;
    private String genreName;
    private String authorName;
    private Integer aiScore;

    // getter and setter
    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAiResult() { return aiResult; }
    public void setAiResult(String aiResult) { this.aiResult = aiResult; }
    public String getReviewOpinion() { return reviewOpinion; }
    public void setReviewOpinion(String reviewOpinion) { this.reviewOpinion = reviewOpinion; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }
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
    public String getWorkTitle() { return workTitle; }
    public void setWorkTitle(String workTitle) { this.workTitle = workTitle; }
    public String getWorkType() { return workType; }
    public void setWorkType(String workType) { this.workType = workType; }
    public String getGenreName() { return genreName; }
    public void setGenreName(String genreName) { this.genreName = genreName; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public Integer getAiScore() { return aiScore; }
    public void setAiScore(Integer aiScore) { this.aiScore = aiScore; }
}
