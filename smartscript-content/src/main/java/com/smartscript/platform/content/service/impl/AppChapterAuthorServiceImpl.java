package com.smartscript.platform.content.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.dto.AppAuthorChapterItem;
import com.smartscript.platform.content.dto.AppChapterCreateRequest;
import com.smartscript.platform.content.dto.AppChapterUpdateRequest;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.service.IAppChapterAuthorService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * App 章节编辑 服务实现（接口文档无章节 CRUD 规格，按模块约定补齐）
 *
 * 依据：云端 script_platform_dev 库 sys_work_chapter / sys_work 表。
 *
 * 反推处理点：
 * 1. 归属以「章节所属作品的 author_id」判定（章节表无 author_id 列）；
 *    作品不存在/已删除按 404，他人作品按 403（本批章节编辑的既定约定）。
 * 2. word_count 由后端按 content 字符长度计算，不接收入参；content 为空时记 0。
 * 3. chapter_no 作品内唯一：前置 countChapterNo 校验（重复 400），并发竞态由唯一键兜底。
 * 4. is_free/status 为 tinyint，按模块约定以字符串 "0"/"1" 落库；新增默认 is_free="0"、status="0"。
 * 5. 新增/更新均落若依审计列 create_by/update_by = 当前用户ID 字符串。
 *
 * @author xiangsipeng
 */
@Service
public class AppChapterAuthorServiceImpl implements IAppChapterAuthorService
{
    /** 是否免费默认值（不免费） */
    private static final String DEFAULT_IS_FREE = "0";

    /** 章节状态默认值（0=正常） */
    private static final String DEFAULT_STATUS = "0";

    @Autowired
    private SysWorkChapterMapper chapterMapper;

    @Autowired
    private SysContentWorkMapper workMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public int resolveWorkAccess(Long workId)
    {
        Long userId = identityProvider.currentUserId();
        if (workId == null || userId == null)
        {
            return ACCESS_NOT_FOUND;
        }
        // selectWorkById 固定过滤 is_deleted=0：已删除作品按不存在处理
        SysWork work = workMapper.selectWorkById(workId);
        if (work == null)
        {
            return ACCESS_NOT_FOUND;
        }
        return Objects.equals(work.getAuthorId(), userId) ? ACCESS_GRANTED : ACCESS_DENIED;
    }

    @Override
    public int resolveChapterAccess(Long chapterId)
    {
        if (chapterId == null)
        {
            return ACCESS_NOT_FOUND;
        }
        SysWorkChapter chapter = chapterMapper.selectChapterMeta(chapterId);
        if (chapter == null)
        {
            return ACCESS_NOT_FOUND;
        }
        return resolveWorkAccess(chapter.getWorkId());
    }

    @Override
    public boolean chapterNoExists(Long workId, Integer chapterNo)
    {
        if (workId == null || chapterNo == null)
        {
            return false;
        }
        return chapterMapper.countChapterNo(workId, chapterNo) > 0;
    }

    @Override
    public List<AppAuthorChapterItem> listAuthorChapters(Long workId)
    {
        List<AppAuthorChapterItem> items = new ArrayList<>();
        if (workId == null)
        {
            return items;
        }
        // status 不传 = 不过滤：作者需看到隐藏章节以便恢复
        SysWorkChapter query = new SysWorkChapter();
        query.setWorkId(workId);
        for (SysWorkChapter row : chapterMapper.selectChapterListByWorkId(query))
        {
            AppAuthorChapterItem item = new AppAuthorChapterItem();
            item.setChapterId(row.getChapterId());
            item.setChapterNo(row.getChapterNo());
            item.setChapterTitle(row.getChapterTitle());
            item.setWordCount(row.getWordCount());
            item.setIsFree(row.getIsFree());
            item.setStatus(row.getStatus());
            items.add(item);
        }
        return items;
    }

    @Override
    public Long createChapter(Long workId, AppChapterCreateRequest request)
    {
        Long userId = identityProvider.currentUserId();
        SysWorkChapter chapter = new SysWorkChapter();
        chapter.setWorkId(workId);
        chapter.setChapterNo(request.getChapterNo());
        chapter.setChapterTitle(request.getChapterTitle());
        chapter.setContent(request.getContent());
        chapter.setWordCount(countWords(request.getContent()));
        chapter.setIsFree(request.getIsFree() == null ? DEFAULT_IS_FREE : request.getIsFree());
        chapter.setStatus(DEFAULT_STATUS);
        chapter.setCreateBy(userId == null ? null : String.valueOf(userId));
        return chapterMapper.insertChapter(chapter) > 0 ? chapter.getChapterId() : null;
    }

    @Override
    public boolean updateChapter(Long chapterId, AppChapterUpdateRequest request)
    {
        Long userId = identityProvider.currentUserId();
        SysWorkChapter update = new SysWorkChapter();
        update.setChapterId(chapterId);
        update.setChapterTitle(request.getChapterTitle());
        update.setContent(request.getContent());
        // 仅在更新正文时重算字数；只改标题/状态时保留原 word_count
        if (request.getContent() != null)
        {
            update.setWordCount(countWords(request.getContent()));
        }
        update.setIsFree(request.getIsFree());
        update.setStatus(request.getStatus());
        update.setUpdateBy(userId == null ? null : String.valueOf(userId));
        return chapterMapper.updateChapter(update) > 0;
    }

    @Override
    public boolean deleteChapter(Long chapterId)
    {
        return chapterMapper.deleteChapter(chapterId) > 0;
    }

    /** 按内容字符长度计算字数（null/空记为 0） */
    private static int countWords(String content)
    {
        return content == null ? 0 : content.length();
    }
}