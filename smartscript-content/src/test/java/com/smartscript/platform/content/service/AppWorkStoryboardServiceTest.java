package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkStoryboard;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysWorkReviewRecordMapper;
import com.smartscript.platform.content.mapper.SysWorkStoryboardMapper;
import com.smartscript.platform.content.service.impl.AppWorkStoryboardServiceImpl;

class AppWorkStoryboardServiceTest
{
    @Test
    void chapterIdentifiersKeepStoryboardAttachedAfterRenameAndRejectMismatches()
    {
        var works = mock(SysContentWorkMapper.class);
        var chapters = mock(SysWorkChapterMapper.class);
        var reviews = mock(SysWorkReviewRecordMapper.class);
        var stories = mock(SysWorkStoryboardMapper.class);
        var work = new SysWork();
        work.setAuthorId(1L);
        when(works.selectWorkById(2L)).thenReturn(work);
        var chapter = new SysWorkChapter();
        chapter.setWorkId(2L);
        chapter.setChapterId(3L);
        chapter.setChapterNo(4);
        chapter.setChapterTitle("改名后的章节");
        when(chapters.selectChapterMeta(3L)).thenReturn(chapter);
        when(chapters.selectChapterByNo(2L, 4)).thenReturn(chapter);
        when(chapters.selectChapterListByWorkId(any())).thenReturn(List.of(chapter));
        when(stories.upsert(any())).thenAnswer(invocation -> {
            SysWorkStoryboard row = invocation.getArgument(0);
            row.setStoryboardId(5L);
            assertEquals(3L, row.getChapterId());
            assertEquals("改名后的章节", row.getChapterTitle());
            when(stories.selectById(5L)).thenReturn(row);
            return 1;
        });
        var service = new AppWorkStoryboardServiceImpl(works, chapters, reviews, stories, null, null);
        TransactionSynchronizationManager.initSynchronization();
        try
        {
            assertEquals(5L, service.save(1L, 2L, 3L, null, "旧标题", "内容", null).getStoryboardId());
            assertEquals(5L, service.save(1L, 2L, null, 4, null, "内容", null).getStoryboardId());
            assertEquals(5L, service.save(1L, 2L, null, null, "改名后的章节", "内容", null).getStoryboardId());
            assertThrows(IllegalArgumentException.class, () -> service.save(1L, 2L, 3L, 6, null, "内容", null));
            assertThrows(IllegalArgumentException.class, () -> service.save(1L, 2L, 99L, null, "改名后的章节", "内容", null));
            verify(stories, times(3)).upsert(any());
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
