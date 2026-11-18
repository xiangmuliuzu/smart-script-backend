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
