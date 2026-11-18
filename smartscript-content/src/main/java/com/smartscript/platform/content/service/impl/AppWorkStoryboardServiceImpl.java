package com.smartscript.platform.content.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkReviewRecord;
import com.smartscript.platform.content.domain.SysWorkStoryboard;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysWorkReviewRecordMapper;
import com.smartscript.platform.content.mapper.SysWorkStoryboardMapper;
import com.smartscript.platform.content.service.IAppWorkStoryboardService;
import com.smartscript.platform.content.util.WorkStatusDeriver;

/** 作品分镜目录服务实现。 */
@Service
public class AppWorkStoryboardServiceImpl implements IAppWorkStoryboardService
{
    private final SysContentWorkMapper workMapper;
    private final SysWorkChapterMapper chapterMapper;
    private final SysWorkReviewRecordMapper reviewRecordMapper;
    private final SysWorkStoryboardMapper storyboardMapper;

    public AppWorkStoryboardServiceImpl(SysContentWorkMapper workMapper,
            SysWorkChapterMapper chapterMapper, SysWorkReviewRecordMapper reviewRecordMapper,
            SysWorkStoryboardMapper storyboardMapper)
    {
        this.workMapper = workMapper;
        this.chapterMapper = chapterMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.storyboardMapper = storyboardMapper;
    }

    @Override
    public List<SysWorkStoryboard> list(Long userId, Long workId)
    {
        requireOwnWork(userId, workId);
        return storyboardMapper.selectByWorkId(workId);
    }

    @Override
    @Transactional
    public SysWorkStoryboard save(Long userId, Long workId, String chapterTitle,
            String content, String source)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId);

        String title = chapterTitle == null ? "" : chapterTitle.trim();
        if (title.isEmpty())
        {
            throw new IllegalArgumentException("请选择已有章节目录标题作为分镜标题");
        }
        if (content == null || content.trim().isEmpty())
        {
            throw new IllegalArgumentException("分镜内容不能为空");
        }

        SysWorkChapter query = new SysWorkChapter();
        query.setWorkId(workId);
        SysWorkChapter chapter = chapterMapper.selectChapterListByWorkId(query).stream()
                .filter(item -> title.equals(item.getChapterTitle() == null ? "" : item.getChapterTitle().trim()))
                .findFirst().orElse(null);
        if (chapter == null)
        {
            throw new IllegalArgumentException("分镜标题必须选择当前作品已有的章节目录标题");
        }

        Date now = new Date();
        String normalizedSource = "ai".equalsIgnoreCase(source) ? "ai" : "manual";
        SysWorkStoryboard existing = storyboardMapper.selectByWorkIdAndChapterId(workId, chapter.getChapterId());
        if (existing == null)
        {
            SysWorkStoryboard created = new SysWorkStoryboard();
            created.setWorkId(workId);
            created.setChapterId(chapter.getChapterId());
            created.setChapterTitle(chapter.getChapterTitle());
            created.setContent(content);
            created.setWordCount(countWords(content));
            created.setSource(normalizedSource);
            created.setStatus("pending");
            created.setCreateBy(String.valueOf(userId));
            created.setCreateTime(now);
            created.setUpdateBy(String.valueOf(userId));
            created.setUpdateTime(now);
            storyboardMapper.insert(created);
            return storyboardMapper.selectById(created.getStoryboardId());
        }

        existing.setChapterTitle(chapter.getChapterTitle());
        existing.setContent(content);
        existing.setWordCount(countWords(content));
        existing.setSource(normalizedSource);
        existing.setStatus("pending");
        existing.setUpdateBy(String.valueOf(userId));
        existing.setUpdateTime(now);
        storyboardMapper.update(existing);
        return storyboardMapper.selectById(existing.getStoryboardId());
    }

    private SysWork requireOwnWork(Long userId, Long workId)
    {
        SysWork work = workMapper.selectWorkById(workId);
        if (work == null)
        {
            throw new SecurityException("作品不存在");
        }
        if (work.getAuthorId() == null || !work.getAuthorId().equals(userId))
        {
            throw new SecurityException("无权访问该作品");
        }
        return work;
    }

    private void requireEditable(Long workId)
    {
        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
        if (!records.isEmpty() && WorkStatusDeriver.isReviewing(records.get(0).getStatus()))
        {
            throw new IllegalStateException("作品正在审核中，不允许保存分镜");
        }
    }

    private int countWords(String value)
    {
        return (int) value.trim().chars()
                .filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c)).count();
    }
}
