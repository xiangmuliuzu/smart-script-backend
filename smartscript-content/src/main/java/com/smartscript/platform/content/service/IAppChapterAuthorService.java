package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.dto.AppAuthorChapterItem;
import com.smartscript.platform.content.dto.AppChapterCreateRequest;
import com.smartscript.platform.content.dto.AppChapterUpdateRequest;

/**
 * App 章节编辑 服务层（接口文档无章节 CRUD 规格，按模块约定补齐，已获授权）
 *
 * 依据：云端 script_platform_dev 库 sys_work_chapter / sys_work 表。
 *
 * 边界：
 *   - App 私有接口，归属取 {@code IdentityProvider.currentUserId()}，不接收请求体 userId；
 *     归属判定：章节所属作品的 author_id 必须等于当前用户（否则 403），作品不存在/已删除按 404。
 *   - word_count 由后端按 content 长度计算，不接收入参；chapter_no 作品内唯一（重复 400）。
 *   - 章节删除为物理删除（表无逻辑删除列）。
 *
 * @author xiangsipeng
 */
public interface IAppChapterAuthorService
{
    /** 归属判定：目标（作品/章节及其所属作品）不存在或已删除 */
    int ACCESS_NOT_FOUND = 0;

    /** 归属判定：目标存在但非本人作品 */
    int ACCESS_DENIED = 1;

    /** 归属判定：目标存在且属于本人作品 */
    int ACCESS_GRANTED = 2;

    /**
     * 判定作品对当前登录身份的归属（新增章节前置校验）
     *
     * @param workId 作品ID
     * @return {@link #ACCESS_NOT_FOUND} / {@link #ACCESS_DENIED} / {@link #ACCESS_GRANTED}
     */
    public int resolveWorkAccess(Long workId);

    /**
     * 判定章节对当前登录身份的归属（更新/删除章节前置校验）
     *
     * 以章节所属作品的作者为归属依据（章节本身无 author_id）。
     *
     * @param chapterId 章节ID
     * @return {@link #ACCESS_NOT_FOUND} / {@link #ACCESS_DENIED} / {@link #ACCESS_GRANTED}
     */
    public int resolveChapterAccess(Long chapterId);

    /**
     * 章节序号是否已存在（chapter_no 作品内唯一）
     *
     * @param workId    作品ID
     * @param chapterNo 章节序号
     * @return true 已存在（应拒绝）
     */
    public boolean chapterNoExists(Long workId, Integer chapterNo);

    /**
     * 作者视角章节列表（不过滤 status，隐藏章节也在列；调用方已校验归属）
     *
     * @param workId 作品ID
     * @return 章节摘要集合（按 chapter_no 升序，不含 content）
     */
    public List<AppAuthorChapterItem> listAuthorChapters(Long workId);

    /**
     * 新增章节
     *
     * @param workId  作品ID（调用方已校验归属）
     * @param request 章节入参（chapterNo/chapterTitle 已由控制层校验）
     * @return 新章节ID；写入失败返回 null
     */
    public Long createChapter(Long workId, AppChapterCreateRequest request);

    /**
     * 更新章节（仅 chapter_title/content/word_count/is_free/status）
     *
     * @param chapterId 章节ID（调用方已校验归属）
     * @param request   更新入参（为 null 的字段不更新；content 更新时重算 word_count）
     * @return true 更新成功
     */
    public boolean updateChapter(Long chapterId, AppChapterUpdateRequest request);

    /**
     * 删除章节（物理删除）
     *
     * @param chapterId 章节ID（调用方已校验归属）
     * @return true 删除成功
     */
    public boolean deleteChapter(Long chapterId);
}