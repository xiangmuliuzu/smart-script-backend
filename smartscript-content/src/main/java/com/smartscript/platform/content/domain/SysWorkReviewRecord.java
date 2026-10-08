package com.smartscript.platform.content.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 作品审核记录对象 sys_review_record
 *
 * 依据：云端 script_platform_dev 库 sys_review_record 表。
 * 表注释：审核记录表（target_type 区分 WORK/SEAL 等业务对象）。
 *
 * 说明：
 * 1. created_at/updated_at 由数据库默认值维护，本实体只读映射 created_at 作为审核提交时间。
 * 2. 本实体供 PC 用户端「作品详情-审核历史」使用，与 smartscript-review 模块的
 *    ReviewRecord 实体解耦（避免跨模块依赖）。
 *
 * @author smartscript
 */
public class SysWorkReviewRecord
{
    /** 审核记录ID */
    private Long reviewId;

    /** 审核编号 */
    private String reviewNo;

    /** 审核对象类型（WORK=作品） */
    private String targetType;

    /** 审核对象ID（作品ID） */
    private Long targetId;

    /** 提交人ID（作者） */
    private Long submitterId;

    /** 审核人ID */
    private Long reviewerId;

    /** AI审核结果（JSON原文本） */
    private String aiResult;

    /** AI风险等级 */
    private String aiRiskLevel;

    /** AI敏感词 */
    private String aiSensitiveWords;

    /** 审核结果（approved/rejected/revision 等） */
    private String reviewResult;

    /** 审核意见 */
    private String reviewOpinion;

    /** 审核状态（pending/ai_reviewing/approved/rejected/revision） */
    private String status;

    /** AI审核开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date aiStartTime;

    /** AI审核结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date aiEndTime;

    /** 人工审核开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewStartTime;

    /** 人工审核结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewEndTime;

    /** 创建时间（数据库维护） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 更新时间（数据库维护） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    // ---- JOIN 扩展字段 ----

    /** 提交人昵称（JOIN sys_user.nick_name） */
    private String submitterName;

    /** 审核人昵称（JOIN sys_user.nick_name） */
    private String reviewerName;

    public SysWorkReviewRecord()
    {
    }

    public Long getReviewId()
    {
        return reviewId;
    }

    public void setReviewId(Long reviewId)
    {
        this.reviewId = reviewId;
    }

    public String getReviewNo()
    {
        return reviewNo;
    }

    public void setReviewNo(String reviewNo)
    {
        this.reviewNo = reviewNo;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public Long getSubmitterId()
    {
        return submitterId;
    }

    public void setSubmitterId(Long submitterId)
    {
        this.submitterId = submitterId;
    }

    public Long getReviewerId()
    {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId)
    {
        this.reviewerId = reviewerId;
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

    public String getReviewResult()
    {
        return reviewResult;
    }

    public void setReviewResult(String reviewResult)
    {
        this.reviewResult = reviewResult;
    }

    public String getReviewOpinion()
    {
        return reviewOpinion;
    }

    public void setReviewOpinion(String reviewOpinion)
    {
        this.reviewOpinion = reviewOpinion;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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

    public Date getReviewStartTime()
    {
        return reviewStartTime;
    }

    public void setReviewStartTime(Date reviewStartTime)
    {
        this.reviewStartTime = reviewStartTime;
    }

    public Date getReviewEndTime()
    {
        return reviewEndTime;
    }

    public void setReviewEndTime(Date reviewEndTime)
    {
        this.reviewEndTime = reviewEndTime;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt)
    {
        this.updatedAt = updatedAt;
    }

    public String getSubmitterName()
    {
        return submitterName;
    }

    public void setSubmitterName(String submitterName)
    {
        this.submitterName = submitterName;
    }

    public String getReviewerName()
    {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName)
    {
        this.reviewerName = reviewerName;
    }
}
