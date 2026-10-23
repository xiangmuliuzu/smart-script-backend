package com.smartscript.platform.content.service;

import java.util.List;
import java.util.Map;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkReviewRecord;

/**
 * PC 用户端「作品详情与审核流程」服务层（A2）
 *
 * 依据：五人分工 A2 + 附件5.1 数据库文档（sys_work / sys_work_chapter / sys_review_record）。
 * 状态机（小写口径，与现有数据库/审核模块一致）：
 *   draft（草稿）可编辑可提交；reviewing（审核中）只读；revision（待修改）可编辑可重新提交；
 *   rejected（已驳回）可编辑可重新提交；published（已上架）只读（修改需重新走审核流程）。
 *
 * @author smartscript
 */
public interface IAppUserWorkService
{
    /**
     * 作品详情（含章节目录、最近审核意见、作品标签）
     *
     * @param userId 当前用户ID（归属校验）
     * @param workId 作品ID
     * @return {work, tags, chapters, latestReview}
     */
    Map<String, Object> getWorkDetail(Long userId, Long workId);

    /**
     * 修改作品基本信息（标题/简介/分类/封面/标签）
     *
     * 仅「审核中」锁定，其余状态（含已发布）均可修改。
     *
     * @param userId  当前用户ID
     * @param workId  作品ID
     * @param title   作品标题（非空）
     * @param summary 作品简介
     * @param genreId 分类ID
     * @param cover   封面地址（2.9.1 上传接口返回的 url；为空不更新）
     * @param tagIds  标签ID列表（接口文档 2.3.3：全量覆盖，最多 8 个；
     *                null = 不修改标签，空列表 = 清空标签）
     */
    void updateWorkBase(Long userId, Long workId, String title, String summary, Integer genreId, String cover,
            List<Integer> tagIds);

    /**
     * 章节目录（含 content 全文，用户端编辑用）
     *
     * @param userId 当前用户ID
     * @param workId 作品ID
     * @return 章节集合（按 chapter_no 升序）
     */
    List<SysWorkChapter> listChapters(Long userId, Long workId);

    /**
     * 新增章节（仅 draft / revision / rejected 状态允许）
     *
     * @param userId       当前用户ID
     * @param workId       作品ID
     * @param chapterTitle 章节标题
     * @param content      章节内容
     * @param isFree       免费试读标记（"0"/"1"，null 默认为 "0"）
     * @return 新增后的章节对象
     */
    SysWorkChapter addChapter(Long userId, Long workId, String chapterTitle, String content, String isFree);

    /**
     * 修改章节（仅 draft / revision / rejected 状态允许）
     *
     * @param userId       当前用户ID
     * @param workId       作品ID
     * @param chapterId    章节ID
     * @param chapterTitle 章节标题
     * @param content      章节内容
     * @param isFree       免费试读标记（"0"/"1"，null 不修改）
     */
    void updateChapter(Long userId, Long workId, Long chapterId, String chapterTitle, String content, String isFree);

    /**
     * 删除章节（仅 draft / revision / rejected 状态允许）
     *
     * @param userId    当前用户ID
     * @param workId    作品ID
     * @param chapterId 章节ID
     */
    void deleteChapter(Long userId, Long workId, Long chapterId);

    /**
     * 提交审核（draft/revision/rejected → reviewing）
     *
     * 审核中禁止重复提交；提交成功后写入 sys_review_record（status='pending'，target_type='work'）。
     *
     * @param userId 当前用户ID
     * @param workId 作品ID
     */
    void submitReview(Long userId, Long workId);

    /**
     * 审核记录（审核历史 + 管理员意见）
     *
     * @param userId 当前用户ID
     * @param workId 作品ID
     * @return 审核记录集合（按创建时间倒序）
     */
    List<SysWorkReviewRecord> listReviewRecords(Long userId, Long workId);

    /**
     * 我的作品列表（A1 工作台「我的作品」页；同时服务 App 端「我的作品」）
     *
     * 过滤口径：
     *   - statusFilter：all / draft / reviewing / revision / rejected / published，
     *     支持逗号分隔多值（如 PC「待修改」= revision,rejected）；null / 空 / all 表示全部。
     *   - keyword：作品标题模糊匹配（忽略大小写），null / 空表示不筛选。
     *   - workType：作品类型精确匹配，null / 空表示不筛选。
     *
     * @param userId       当前用户ID
     * @param statusFilter 状态过滤（支持逗号多值）
     * @param keyword      标题关键词（可空）
     * @param workType     作品类型（可空）
     * @return 作品列表（含派生 status、分类名、审核意见摘要、创建/更新时间），按更新时间倒序
     */
    List<Map<String, Object>> listWorks(Long userId, String statusFilter, String keyword, String workType);

    /**
     * 工作台作品统计（A1 工作台首页统计卡片）
     *
     * 口径：「待修改」= revision + rejected（与 PC 作品列表「待修改」Tab 一致）。
     *
     * @param userId 当前用户ID
     * @return {workTotal, draftCount, reviewingCount, revisionCount, publishedCount}
     */
    Map<String, Object> workStatistics(Long userId);

    /**
     * 作者申请下架 / 重新上架作品
     *
     * - 下架（on_shelf → off_shelf）：只降低可见性，无前置条件，且不修改审核记录，
     *   因此作者可自助重新上架、免重审；
     * - 重新上架（off_shelf → on_shelf）：要求最新审核记录为 approved，
     *   且 reject_reason 为空（有值表示平台强制下架，须改完重新提交审核）。
     *
     * @param userId 当前用户ID
     * @param workId 作品ID
     * @param status 目标状态：on_shelf / off_shelf
     */
    void changeShelfStatus(Long userId, Long workId, String status);
}
