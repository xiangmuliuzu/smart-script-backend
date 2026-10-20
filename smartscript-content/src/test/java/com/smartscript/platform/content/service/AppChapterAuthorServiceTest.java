package com.smartscript.platform.content.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.dto.AppChapterCreateRequest;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.service.impl.AppChapterAuthorServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * App 章节编辑服务层单元测试（作品生命周期重构）。
 *
 * AppChapterAuthorServiceImpl 使用 @Autowired 字段注入，测试内用 ReflectionTestUtils 注入
 * chapterMapper / workMapper / identityProvider 三个 mock。
 * 关注点：新增章节默认 status='1'（草稿，仅作者可见），并回写作品聚合列。
 */
class AppChapterAuthorServiceTest
{
    private static final Long USER_ID = 1L;
    private static final Long WORK_ID = 1001L;

    private SysWorkChapterMapper chapterMapper;
    private SysContentWorkMapper workMapper;
    private IdentityProvider identityProvider;
    private AppChapterAuthorServiceImpl service;

    @BeforeEach
    void setUp()
    {
        chapterMapper = mock(SysWorkChapterMapper.class);
        workMapper = mock(SysContentWorkMapper.class);
        identityProvider = mock(IdentityProvider.class);
        service = new AppChapterAuthorServiceImpl();
        ReflectionTestUtils.setField(service, "chapterMapper", chapterMapper);
        ReflectionTestUtils.setField(service, "workMapper", workMapper);
        ReflectionTestUtils.setField(service, "identityProvider", identityProvider);
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
    }

    @Test
    @DisplayName("新增章节：默认 status='1'（草稿），入库成功后回写作品聚合列")
    void createChapterDefaultsToDraftStatus()
    {
        when(chapterMapper.insertChapter(any(SysWorkChapter.class))).thenReturn(1);

        AppChapterCreateRequest request = new AppChapterCreateRequest();
        request.setChapterNo(1);
        request.setChapterTitle("第一章");
        request.setContent("内容");

        service.createChapter(WORK_ID, request);

        verify(chapterMapper).insertChapter(argThat(c -> "1".equals(c.getStatus())));
        verify(workMapper).refreshWorkAggregates(WORK_ID);
    }
}
