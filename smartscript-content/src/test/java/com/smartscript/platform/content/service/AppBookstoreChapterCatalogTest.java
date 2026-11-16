package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.dto.AppChapterDto;
import com.smartscript.platform.content.dto.AppChapterListDto;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.mapper.AppReadRecordMapper;
import com.smartscript.platform.content.mapper.AppWorkAuthorizationMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.service.impl.AppBookstoreServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 作品章节目录展示范围单元测试。
 *
 * 需求口径：
 *   - 未获版权授权：目录仅展示可试读章节（平台试读范围 + 作者标记免费），
 *     已发布但未开放试读的章节不下发；
 *   - 已获版权授权：展示全部已发布章节，且全部可读。
 */
class AppBookstoreChapterCatalogTest
{
    private static final Long USER_ID = 7L;
    private static final Long WORK_ID = 1001L;

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
    }

    private AppWorkDto work(String previewEnabled, Integer previewEpisodes)
    {
        AppWorkDto work = new AppWorkDto();
        work.setWorkId(WORK_ID);
        work.setPreviewEnabled(previewEnabled);
        work.setPreviewEpisodes(previewEpisodes);
        return work;
    }

    private SysWorkChapter chapter(long chapterId, int chapterNo, String isFree)
    {
        SysWorkChapter chapter = new SysWorkChapter();
        chapter.setChapterId(chapterId);
        chapter.setWorkId(WORK_ID);
        chapter.setChapterNo(chapterNo);
        chapter.setChapterTitle("第" + chapterNo + "章");
        chapter.setStatus("0");
        chapter.setIsFree(isFree);
        chapter.setContent("正文内容");
        return chapter;
    }

    private void stubChapters()
    {
        when(chapterMapper.selectChapterListByWorkId(any())).thenReturn(List.of(
                chapter(2001L, 1, "1"),
                chapter(2002L, 2, "0"),
                chapter(2003L, 3, "1")));
    }

    @Test
    @DisplayName("未获授权：目录只下发作者标记的试读章节，付费章节隐藏")
    void previewOnlyHidesPaidChapters()
    {
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
        when(authorizationMapper.countEffectiveAuthorization(WORK_ID, USER_ID)).thenReturn(0);
        stubChapters();

        AppChapterListDto dto = service.getChapterCatalog(work("0", 0));

        assertEquals(3, dto.getPublishedTotal());
        assertEquals(2, dto.getTotal());
        assertEquals(List.of(2001L, 2003L), dto.getList().stream().map(AppChapterDto::getChapterId).toList());
        assertTrue(dto.getList().stream().allMatch(chapter -> Boolean.TRUE.equals(chapter.getReadable())));
    }

    @Test
    @DisplayName("已获版权授权：目录下发全部已发布章节且全部可读")
    void authorizedUnlocksAllChapters()
    {
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
        when(authorizationMapper.countEffectiveAuthorization(WORK_ID, USER_ID)).thenReturn(1);
        stubChapters();

        AppChapterListDto dto = service.getChapterCatalog(work("0", 0));

        assertEquals(3, dto.getPublishedTotal());
        assertEquals(3, dto.getTotal());
        assertEquals(List.of(2001L, 2002L, 2003L),
                dto.getList().stream().map(AppChapterDto::getChapterId).toList());
        assertTrue(dto.getList().stream().allMatch(chapter -> Boolean.TRUE.equals(chapter.getReadable())));
    }

    @Test
    @DisplayName("已完成版权订单：视同已购，解锁全部章节")
    void completedOrderUnlocksAllChapters()
    {
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
        when(authorizationMapper.countEffectiveAuthorization(WORK_ID, USER_ID)).thenReturn(0);
        when(authorizationMapper.countPurchasedOrder(WORK_ID, USER_ID)).thenReturn(1);
        stubChapters();

        AppChapterListDto dto = service.getChapterCatalog(work("0", 0));

        assertEquals(3, dto.getTotal());
        assertTrue(dto.getList().stream().allMatch(chapter -> Boolean.TRUE.equals(chapter.getReadable())));
    }

    @Test
    @DisplayName("平台试读范围未授权时同样只下发试读范围章节")
    void platformPreviewRangeLimitsCatalog()
    {
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
        when(authorizationMapper.countEffectiveAuthorization(WORK_ID, USER_ID)).thenReturn(0);
        when(chapterMapper.selectChapterListByWorkId(any())).thenReturn(List.of(
                chapter(2001L, 1, "0"),
                chapter(2002L, 2, "0"),
                chapter(2003L, 3, "0")));

        AppChapterListDto dto = service.getChapterCatalog(work("1", 2));

        assertEquals(3, dto.getPublishedTotal());
        assertEquals(2, dto.getTotal());
        assertEquals(List.of(2001L, 2002L), dto.getList().stream().map(AppChapterDto::getChapterId).toList());
    }
}
