package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysWorkChapter;

/**
 * 作品章节 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_work_chapter 表。
 * 作品管理页"章节查看"只读使用；App 端章节目录与试读正文共用本层。
 * App 章节编辑（接口文档无 CRUD 规格，按模块约定补齐）复用本层的写方法，
 * 不为同一张表另起 Mapper。
 *
 * @author xiangsipeng
 */
public interface SysWorkChapterMapper
{
    /**
     * 按作品ID查询章节列表（分页，不含 content 全文）
     *
     * @param query 查询条件（workId 必传）
     * @return 章节集合
     */
    public List<SysWorkChapter> selectChapterListByWorkId(SysWorkChapter query);

    /**
     * 按章节ID查询章节正文（含 content 全文）
     *
     * 不加作品可见性条件：作品是否上架由服务层统一口径判定，
     * 以便区分「章节不存在」与「作品已下架」两种 404 语义。
     *
     * @param chapterId 章节ID
     * @return 章节对象（含 content），不存在返回 null
     */
    public SysWorkChapter selectChapterDetail(Long chapterId);

    /**
     * 按作品ID查询章节列表（含 content 全文，用户端编辑用）
     *
     * @param workId 作品ID
     * @return 章节集合（按 chapter_no 升序）
     */
    public List<SysWorkChapter> selectChapterListWithContentByWorkId(Long workId);

    /**
     * 查询作品最大章节序号
     *
     * @param workId 作品ID
     * @return 最大 chapter_no，无章节返回 0
     */
    public Integer selectMaxChapterNo(Long workId);

    // ==================== App 章节编辑（接口文档无 CRUD 规格，按模块约定补齐） ====================

    /**
     * 按章节ID查询章节元信息（不含 content 全文）
     *
     * 用于章节编辑的归属判定：只需 work_id 反查作品作者，无需拉取长文本。
     *
     * @param chapterId 章节ID
     * @return 章节对象（无 content），不存在返回 null
     */
    public SysWorkChapter selectChapterMeta(Long chapterId);

    /**
     * 统计作品内章节序号是否已存在（唯一键 uk_work_chapter_no 前置校验）
     *
     * @param workId    作品ID
     * @param chapterNo 章节序号
     * @return 记录数（1=已存在）
     */
    public int countChapterNo(@Param("workId") Long workId, @Param("chapterNo") Integer chapterNo);

    /**
     * 新增章节（用户端草稿/待修改编辑，回填自增 chapterId）
     *
     * @param chapter 章节对象（word_count/status 由服务层给定）
     * @return 影响行数
     */
    public int insertChapter(SysWorkChapter chapter);

    /**
     * 修改章节（用户端草稿/待修改编辑）
     *
     * 为 null 的字段不更新；章节序号由调用方决定是否传入（App 章节编辑不传，不改 work_id/chapter_no）。
     *
     * @param chapter 仅携带 chapterId + 待更新字段 + updateBy
     * @return 影响行数
     */
    public int updateChapter(SysWorkChapter chapter);

    /**
     * 删除章节（物理删除：sys_work_chapter 无逻辑删除列）
     *
     * @param chapterId 章节ID
     * @return 影响行数
     */
    public int deleteChapterById(Long chapterId);

    /**
     * 删除章节（同上，章节编辑使用）
     *
     * @param chapterId 章节ID
     * @return 影响行数
     */
    public int deleteChapter(@Param("chapterId") Long chapterId);
}