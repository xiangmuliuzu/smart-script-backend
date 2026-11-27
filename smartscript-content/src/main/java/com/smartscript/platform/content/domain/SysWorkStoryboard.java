package com.smartscript.platform.content.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 作品分镜目录对象 sys_work_storyboard。
 *
 * 分镜与章节同级保存，但每条分镜必须关联当前作品已有章节。
 * status 使用 pending/approved/rejected 表示分镜审核状态。
 */
public class SysWorkStoryboard extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long storyboardId;
    private Long workId;
    private Long chapterId;
    private String chapterTitle;
    private String content;
    private Integer wordCount;
    private String source;
    private String status;
    private Long reviewerId;
    private Date reviewTime;
    private String rejectReason;
    /** AI 审核结果 JSON；规则、敏感词和问题定位沿用作品审核格式。 */
    private String aiResult;
    private String aiRiskLevel;
    private String aiSensitiveWords;
    private Date aiStartTime;
    private Date aiEndTime;
    /** 是否删除（0=否，1=是）；分镜采用逻辑删除，避免误删后无法追溯。 */
    private String isDeleted;
    /** 管理端查询扩展字段。 */
    private String workTitle;
    private String authorName;
    /** 管理端兼容查询字段；分镜展示标题实际使用关联章节标题。 */
    private String title;

    public Long getStoryboardId()
    {
        return storyboardId;
    }

    public void setStoryboardId(Long storyboardId)
    {
        this.storyboardId = storyboardId;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public Long getChapterId()
    {
        return chapterId;
    }

    public void setChapterId(Long chapterId)
    {
        this.chapterId = chapterId;
    }

    public String getChapterTitle()
    {
        return chapterTitle;
    }

    public void setChapterTitle(String chapterTitle)
    {
        this.chapterTitle = chapterTitle;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Integer getWordCount()
    {
        return wordCount;
    }

    public void setWordCount(Integer wordCount)
    {
        this.wordCount = wordCount;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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

    public String getAiResult()
    {
        return aiResult;
    }

    public void setAiResult(String aiResult)
    {
        this.aiResult = aiResult;
    }

    public String getAiRiskLevel()
    {
        return aiRiskLevel;
    }

    public void setAiRiskLevel(String aiRiskLevel)
    {
        this.aiRiskLevel = aiRiskLevel;
    }

    public String getAiSensitiveWords()
    {
        return aiSensitiveWords;
    }

    public void setAiSensitiveWords(String aiSensitiveWords)
    {
        this.aiSensitiveWords = aiSensitiveWords;
    }

    public Date getAiStartTime()
    {
        return aiStartTime;
    }

    public void setAiStartTime(Date aiStartTime)
    {
        this.aiStartTime = aiStartTime;
    }

    public Date getAiEndTime()
    {
        return aiEndTime;
    }

    public void setAiEndTime(Date aiEndTime)
    {
        this.aiEndTime = aiEndTime;
    }

    public String getIsDeleted()
    {
        return isDeleted;
    }

    public void setIsDeleted(String isDeleted)
    {
        this.isDeleted = isDeleted;
    }

    public String getWorkTitle()
    {
        return workTitle;
    }

    public void setWorkTitle(String workTitle)
    {
        this.workTitle = workTitle;
    }

    public String getAuthorName()
    {
        return authorName;
    }

    public void setAuthorName(String authorName)
    {
        this.authorName = authorName;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("storyboardId", storyboardId)
                .append("workId", workId)
                .append("chapterId", chapterId)
                .append("chapterTitle", chapterTitle)
                .append("wordCount", wordCount)
                .append("source", source)
                .append("status", status)
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
