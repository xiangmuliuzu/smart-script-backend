package com.smartscript.platform.content.dto;

/**
 * 审核状态查询（App 接口文档 2.9.6 表 2-116）。
 *
 * 字段映射（sys_work 无 review_result/review_comment 列，按现有列派生，零表结构变更）：
 *   - status         ← sys_work.status（PRD 9.3 作品状态枚举）
 *   - reviewComment  ← sys_work.reject_reason（驳回/发回意见）
 *   - reviewResult   ← 由 status 派生：approved/published→approved；rejected→rejected；其余→pending
 *
 * @author xiangsipeng
 */
public class AppReviewStatusDto
{
    /** 审核状态（作品状态枚举原值） */
    private String status;

    /** 审核结果（approved / rejected / pending） */
    private String reviewResult;

    /** 审核意见（驳回或发回原因，无则为 null） */
    private String reviewComment;

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getReviewResult()
    {
        return reviewResult;
    }

    public void setReviewResult(String reviewResult)
    {
        this.reviewResult = reviewResult;
    }

    public String getReviewComment()
    {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment)
    {
        this.reviewComment = reviewComment;
    }
}