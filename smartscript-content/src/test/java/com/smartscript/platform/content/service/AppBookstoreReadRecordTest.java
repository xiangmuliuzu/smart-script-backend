package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.dto.AppChapterDetailDto;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.mapper.AppReadRecordMapper;
import com.smartscript.platform.content.mapper.AppWorkAuthorizationMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.service.impl.AppBookstoreServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 章节正文阅读埋点单元测试（sys_read_record 写入）。
 *
 * 校验点与 spec 决策 1 对齐：
 *   - 登录用户读到可读章节 → 补写 1 条阅读记录；
 *   - 游客读同一章节 → 不写（不伪造用户 ID）；
 *   - 章节不可读（正文未下发）→ 不写；
 *   - 埋点写入异常 → 正文照常返回，不向上抛出。
 */
class AppBookstoreReadRecordTest
{
    private static final Long USER_ID = 7L;
    private static final Long WORK_ID = 1001L;
    private static final Long CHAPTER_ID = 2001L;

    private SysWorkChapterMapper chapterMapper;
    private SysContentWorkMapper workMapper;
    private SysTagMapper tagMapper;
    private AppWorkAuthorizationMapper authorizationMapper;
    private AppReadRecordMapper readRecordMapper;
    private IdentityProvider identityProvider;
    private AppBookstoreServiceImpl service;

    @BeforeEach
    void setUp()
    {
        chapterMapper = mock(SysWorkChapterMapper.class);
        workMapper = mock(SysContentWorkMapper.class);
        tagMapper = mock(SysTagMapper.class);
        authorizationMapper = mock(AppWorkAuthorizationMapper.class);
        readRecordMapper = mock(AppReadRecordMapper.class);
        identityProvider = mock(IdentityProvider.class);
        service = new AppBookstoreServiceImpl();
        ReflectionTestUtils.setField(service, "chapterMapper", chapterMapper);
        ReflectionTestUtils.setField(service, "workMapper", workMapper);
        ReflectionTestUtils.setField(service, "tagMapper", tagMapper);
        ReflectionTestUtils.setField(service, "authorizationMapper", authorizationMapper);
        ReflectionTestUtils.setField(service, "readRecordMapper", readRecordMapper);
        ReflectionTestUtils.setField(service, "identityProvider", identityProvider);
        // 标签回填为批量查询，未命中时返回空集合
        when(tagMapper.selectWorkTagRows(any())).thenReturn(List.of());
        // 作品侧固定：可见作品、无免费章节汇总
        when(workMapper.selectAppWorkById(WORK_ID)).thenReturn(visibleWork());
        when(chapterMapper.countFreeChapters(WORK_ID)).thenReturn(0);
    }

    private AppWorkDto visibleWork()
    {
        AppWorkDto work = new AppWorkDto();
        work.setWorkId(WORK_ID);
        // previewEnabled 留空（=未开启试读），可读性仅由免费标记/授权决定
        return work;
    }

    private SysWorkChapter chapter(String isFree)
    {
        SysWorkChapter chapter = new SysWorkChapter();
        chapter.setChapterId(CHAPTER_ID);
        chapter.setWorkId(WORK_ID);
        chapter.setChapterNo(1);
        chapter.setChapterTitle("第一章");
        chapter.setStatus("0");
        chapter.setIsFree(isFree);
        chapter.setContent("正文内容");
        return chapter;
    }

    @Test
    @DisplayName("登录用户读免费章节：正文下发且补写 1 条阅读记录")
    void loggedInReadWritesRecord()
    {
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
        when(chapterMapper.selectChapterDetail(CHAPTER_ID)).thenReturn(chapter("1"));

        AppChapterDetailDto dto = service.getChapterContent(CHAPTER_ID);

        assertNotNull(dto);
        assertTrue(dto.getReadable());
        assertNotNull(dto.getContent());
        verify(readRecordMapper).insertReadRecordIfAbsent(USER_ID, WORK_ID, CHAPTER_ID);
    }

    @Test
    @DisplayName("游客读免费章节：正文下发但不写阅读记录")
    void guestReadDoesNotWriteRecord()
    {
        when(identityProvider.currentUserId()).thenReturn(null);
        when(chapterMapper.selectChapterDetail(CHAPTER_ID)).thenReturn(chapter("1"));

        AppChapterDetailDto dto = service.getChapterContent(CHAPTER_ID);

        assertNotNull(dto);
        assertTrue(dto.getReadable());
        verify(readRecordMapper, never()).insertReadRecordIfAbsent(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("付费且未授权章节：正文不下发，不写阅读记录")
    void unreadableChapterDoesNotWriteRecord()
    {
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
        when(authorizationMapper.countEffectiveAuthorization(WORK_ID, USER_ID)).thenReturn(0);
        when(chapterMapper.selectChapterDetail(CHAPTER_ID)).thenReturn(chapter("0"));

        AppChapterDetailDto dto = service.getChapterContent(CHAPTER_ID);

        assertNotNull(dto);
        assertFalse(dto.getReadable());
        assertNull(dto.getContent());
        verify(readRecordMapper, never()).insertReadRecordIfAbsent(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("埋点写入异常：正文照常返回，不向上抛出")
    void writeFailureDoesNotBreakContent()
    {
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
        when(chapterMapper.selectChapterDetail(CHAPTER_ID)).thenReturn(chapter("1"));
        when(readRecordMapper.insertReadRecordIfAbsent(USER_ID, WORK_ID, CHAPTER_ID))
                .thenThrow(new RuntimeException("db down"));

        AppChapterDetailDto dto = service.getChapterContent(CHAPTER_ID);

        assertNotNull(dto);
        assertTrue(dto.getReadable());
        assertEquals("正文内容", dto.getContent());
    }
}
