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
     * 作品详情（含章节目录、最近审核意见）
     *
     * @param userId 当前用户ID（归属校验）
     * @param workId 作品ID
     * @return {work, chapters, latestReview}
     */
    Map<String, Object> getWorkDetail(Long userId, Long workId);

    /**
     * 修改作品基本信息（标题/简介/分类）
     *
     * 仅 draft / revision / rejected 状态允许。
     *
     * @param userId  当前用户ID
     * @param workId  作品ID
     * @param title   作品标题（非空）
     * @param summary 作品简介
     * @param genreId 分类ID
     */
    void updateWorkBase(Long userId, Long workId, String title, String summary, Integer genreId);

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
     * @return 新增后的章节对象
     */
    SysWorkChapter addChapter(Long userId, Long workId, String chapterTitle, String content);

    /**
     * 修改章节（仅 draft / revision / rejected 状态允许）
     *
     * @param userId       当前用户ID
     * @param workId       作品ID
     * @param chapterId    章节ID
     * @param chapterTitle 章节标题
     * @param content      章节内容
     */
    void updateChapter(Long userId, Long workId, Long chapterId, String chapterTitle, String content);

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
     * 我的作品列表（按审核派生状态过滤）
     *
     * @param userId      当前用户ID
     * @param statusFilter 状态过滤：all / draft / reviewing / revision / rejected / published（null 或 all 表示全部）
     * @return 作品列表（含派生 status、作者昵称、分类名、更新时间）
     */
    List<Map<String, Object>> listWorks(Long userId, String statusFilter);
}
