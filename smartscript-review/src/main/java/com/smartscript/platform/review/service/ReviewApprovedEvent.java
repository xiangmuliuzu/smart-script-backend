package com.smartscript.platform.review.service;

/**
 * 作品审核通过事件。
 *
 * 审核模块只发布业务事件，不直接依赖用户消息模块；用户模块监听该事件后
 * 写入已有的站内通知表。审核通过与作品上架是两个独立动作。
 */
public final class ReviewApprovedEvent
{
    private final Long reviewId;
    private final Long workId;
    private final Long authorId;
    private final String workTitle;

    public ReviewApprovedEvent(Long reviewId, Long workId, Long authorId, String workTitle)
    {
        this.reviewId = reviewId;
        this.workId = workId;
        this.authorId = authorId;
        this.workTitle = workTitle;
    }

    public Long getReviewId() { return reviewId; }
    public Long getWorkId() { return workId; }
    public Long getAuthorId() { return authorId; }
    public String getWorkTitle() { return workTitle; }
}
