package com.smartscript.platform.content.util;

import java.util.Set;

/**
 * 作品 / 章节状态口径的唯一定义处（内容模块状态不变式）。
 *
 * 两个正交维度：
 * 1. 可见性开关：{@code sys_work.status} ∈ {on_shelf, off_shelf}，只表示上架/下架，
 *    不承载生命周期，也不承载审核状态。
 * 2. 生命周期：由 {@code sys_review_record}（target_type='work'）最新一条的 status 派生，
 *    见 {@link #derive(String)}。
 *
 * <p>读者可见公式：{@code is_deleted=0 AND status='on_shelf' AND 最新审核=approved}，
 * 见 {@link #isReaderVisible(String, String)}；章节再加 {@code sys_work_chapter.status='0'}，
 * 见 {@link #isChapterReaderVisible(String)}。</p>
 *
 * <p>对应 SQL 侧的同口径实现在
 * {@code mapper/content/SysContentWorkMapper.xml} 的 {@code readerVisibleWork} 片段，
 * 二者必须保持一致。</p>
 *
 * @author xiangsipeng
 */
public final class WorkStatusDeriver
{
    /** 可见性开关：已上架 */
    public static final String ON_SHELF = "on_shelf";

    /** 可见性开关：已下架（含作者申请下架与平台强制下架） */
    public static final String OFF_SHELF = "off_shelf";

    /** 生命周期：草稿（从未提交审核） */
    public static final String DRAFT = "draft";

    /** 生命周期：审核中（不可编辑、不可重复提交） */
    public static final String REVIEWING = "reviewing";

    /** 生命周期：待修改（原样返回 revision） */
    public static final String REVISION = "revision";

    /** 生命周期：已驳回（原样返回 rejected） */
    public static final String REJECTED = "rejected";

    /** 生命周期：已发布（最新审核为 approved） */
    public static final String PUBLISHED = "published";

    /** 审核记录：已通过（审核流转终态） */
    public static final String APPROVED = "approved";

    /** 章节状态：已发布（读者可见） */
    public static final String CHAPTER_PUBLISHED = "0";

    /** 章节状态：草稿（仅作者可见，发布由 PUT /chapters/{chapterId} 传 status='0' 触发） */
    public static final String CHAPTER_DRAFT = "1";

    /** 审核记录中属于「审核中」的状态值 */
    private static final Set<String> REVIEWING_REVIEW_STATUS = Set.of("pending", "ai_reviewing");

    private WorkStatusDeriver()
    {
    }

    /**
     * 由最新一条审核记录的 status 派生生命周期状态。
     *
     * @param latestReviewStatus 最新审核记录 status；无记录时传 null
     * @return draft / reviewing / published，或原样返回 revision / rejected
     */
    public static String derive(String latestReviewStatus)
    {
        if (latestReviewStatus == null || latestReviewStatus.isBlank())
        {
            return DRAFT;
        }
        if (REVIEWING_REVIEW_STATUS.contains(latestReviewStatus))
        {
            return REVIEWING;
        }
        if (APPROVED.equals(latestReviewStatus))
        {
            return PUBLISHED;
        }
        // revision / rejected 原样返回
        return latestReviewStatus;
    }

    /**
     * 最新审核记录是否处于「审核中」（此时作品锁定编辑、不可重复提交）。
     *
     * @param latestReviewStatus 最新审核记录 status；无记录时传 null
     * @return true=审核中
     */
    public static boolean isReviewing(String latestReviewStatus)
    {
        return latestReviewStatus != null && REVIEWING_REVIEW_STATUS.contains(latestReviewStatus);
    }

    /**
     * 作品对读者是否可见：已上架且最新审核已通过。
     *
     * @param workStatus         sys_work.status（可见性开关）
     * @param latestReviewStatus 最新审核记录 status；无记录时传 null
     * @return true=读者可见
     */
    public static boolean isReaderVisible(String workStatus, String latestReviewStatus)
    {
        return ON_SHELF.equals(workStatus) && APPROVED.equals(latestReviewStatus);
    }

    /**
     * 章节对读者是否可见：仅「已发布」章节可见，「草稿」章节只有作者自己能看到。
     *
     * @param chapterStatus sys_work_chapter.status（tinyint 以字符串持有）
     * @return true=读者可见
     */
    public static boolean isChapterReaderVisible(String chapterStatus)
    {
        return !CHAPTER_DRAFT.equals(chapterStatus);
    }
}
