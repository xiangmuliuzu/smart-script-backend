package com.smartscript.platform.content.dto;

import java.util.Date;

/**
 * 分镜目录对外返回对象。
 *
 * 分镜和章节共用作品的试读边界：只有审核通过的分镜才进入公开目录，
 * readable=false 时不下发 content，避免把未开放的分镜正文泄露给读者。
 * 作者查看自己的作品时保留完整内容和审核信息，供创作端继续编辑或查看审核结果。
 */
public class AppStoryboardDto
{
    private Long storyboardId;
    private Long workId;
    private Long chapterId;
    private Integer chapterNo;
    private String chapterTitle;
    private String content;
    private Integer wordCount;
    private String source;
    private String status;
    /**
     * 当前分镜是否开放公开试读。
     *
     * 该字段与 readable 不同：readable 表示当前请求人是否能读到正文，
     * 作者本人即使没有开放试读也可能 readable=true；trialEnabled 才表示
     * 关联章节是否已配置为公开试读。
     */
    private Boolean trialEnabled;
    private Boolean readable;
    private Long reviewerId;
    private Date reviewTime;
    private String rejectReason;
    private String aiResult;
    private String aiRiskLevel;
    private String aiSensitiveWords;
    private Date aiStartTime;
    private Date aiEndTime;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String remark;

    public Long getStoryboardId() { return storyboardId; }
    public void setStoryboardId(Long storyboardId) { this.storyboardId = storyboardId; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Integer getChapterNo() { return chapterNo; }
    public void setChapterNo(Integer chapterNo) { this.chapterNo = chapterNo; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getTrialEnabled() { return trialEnabled; }
    public void setTrialEnabled(Boolean trialEnabled) { this.trialEnabled = trialEnabled; }
    public Boolean getReadable() { return readable; }
    public void setReadable(Boolean readable) { this.readable = readable; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Date getReviewTime() { return reviewTime; }
    public void setReviewTime(Date reviewTime) { this.reviewTime = reviewTime; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getAiResult() { return aiResult; }
    public void setAiResult(String aiResult) { this.aiResult = aiResult; }
    public String getAiRiskLevel() { return aiRiskLevel; }
    public void setAiRiskLevel(String aiRiskLevel) { this.aiRiskLevel = aiRiskLevel; }
    public String getAiSensitiveWords() { return aiSensitiveWords; }
    public void setAiSensitiveWords(String aiSensitiveWords) { this.aiSensitiveWords = aiSensitiveWords; }
    public Date getAiStartTime() { return aiStartTime; }
    public void setAiStartTime(Date aiStartTime) { this.aiStartTime = aiStartTime; }
    public Date getAiEndTime() { return aiEndTime; }
    public void setAiEndTime(Date aiEndTime) { this.aiEndTime = aiEndTime; }
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
