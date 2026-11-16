package com.smartscript.platform.content.dto;

/**
 * 审核状态查询（App 接口文档 2.9.6 表 2-116）。
 *
 * 字段映射（sys_work 无 review_result/review_comment 列，按现有列派生，零表结构变更）：
 *   - status         ← 由 sys_review_record（target_type='work'）最新一条派生（见 WorkStatusDeriver）：
 *                     无记录→draft；pending/ai_reviewing→reviewing；approved→published；revision/rejected 原样
 *   - reviewComment  ← sys_work.reject_reason（驳回/发回意见）
 *   - reviewResult   ← 由最新审核记录 status 派生：approved→approved；rejected→rejected；其余→pending
 *
 * 注意：sys_work.status 只是可见性开关（on_shelf/off_shelf），不是审核状态，勿再用于本 DTO。
 *
 * @author xiangsipeng
 */
public class AppReviewStatusDto
{
    /** 作品生命周期状态（由最新审核记录派生：draft/reviewing/revision/rejected/published） */
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