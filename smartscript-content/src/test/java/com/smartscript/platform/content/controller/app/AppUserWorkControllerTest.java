package com.smartscript.platform.content.controller.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.service.IAppUserWorkService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * PC 创作者端「新增章节」接口入参测试。
 *
 * 覆盖 Web 端新建章节的 chapterNo 透传：显式序号按序落库；非法序号返回 400；
 * 未传序号（或请求体缺失）时追加到末尾。
 * 直接调控制器方法（无需 Spring 上下文），与既有控制器测试风格一致。
 */
class AppUserWorkControllerTest
{
    private static final Long OWNER = 1L;
    private static final Long WORK_ID = 1001L;

    private IAppUserWorkService service;
    private IdentityProvider identityProvider;
    private AppUserWorkController controller;

    @BeforeEach
    void setUp()
    {
        service = mock(IAppUserWorkService.class);
        identityProvider = mock(IdentityProvider.class);
        controller = new AppUserWorkController(service, identityProvider);
        when(identityProvider.currentUserId()).thenReturn(OWNER);
        when(service.addChapter(anyLong(), anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(new SysWorkChapter());
    }

    private static Map<String, Object> bodyOf(Object chapterNo)
    {
        Map<String, Object> body = new HashMap<>();
        body.put("chapterTitle", "新章节");
        body.put("content", "正文");
        if (chapterNo != null)
        {
            body.put("chapterNo", chapterNo);
        }
        return body;
    }

    @Test
    @DisplayName("新增章节：显式 chapterNo 透传服务层")
    void addChapterPassesExplicitNo()
    {
        AppApiResponse<SysWorkChapter> response = controller.addChapter(WORK_ID, bodyOf(5));

        assertEquals(200, response.getCode());
        verify(service).addChapter(OWNER, WORK_ID, 5, "新章节", "正文", null, null);
    }

    @Test
    @DisplayName("新增章节：非法 chapterNo（0/负数/非数字）返回 400 且不触达服务层")
    void addChapterRejectsInvalidNo()
    {
        assertEquals(400, controller.addChapter(WORK_ID, bodyOf(0)).getCode());
        assertEquals(400, controller.addChapter(WORK_ID, bodyOf(-1)).getCode());
        assertEquals(400, controller.addChapter(WORK_ID, bodyOf("abc")).getCode());
        verify(service, never()).addChapter(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("新增章节：未传 chapterNo 或请求体缺失时按 null 追加")
    void addChapterWithoutNoAppends()
    {
        assertEquals(200, controller.addChapter(WORK_ID, bodyOf(null)).getCode());
        assertEquals(200, controller.addChapter(WORK_ID, null).getCode());
        verify(service).addChapter(eq(OWNER), eq(WORK_ID), isNull(), eq("新章节"), eq("正文"), isNull(), isNull());
        verify(service).addChapter(eq(OWNER), eq(WORK_ID), isNull(), isNull(), isNull(), isNull(), isNull());
    }
}
