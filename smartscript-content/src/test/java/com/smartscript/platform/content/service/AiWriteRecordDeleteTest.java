package com.smartscript.platform.content.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.controller.app.AppAiController;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.service.IAiWriteService;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * AI 记录批量删除测试（PC 创作者端「记录管理」）。
 *
 * 不连库：mapper 与身份均 mock。服务层验证归属条件、脏 ID 过滤与空入参短路；
 * 控制器验证请求体解析（数字/字符串/脏 ID），避免线上因入参类型拿到 500。
 */
class AiWriteRecordDeleteTest
{
    private static final Long USER_ID = 106L;

    private AppAiWriteRecordMapper writeRecordMapper;
    private IdentityProvider identityProvider;
    private AiWriteServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        writeRecordMapper = mock(AppAiWriteRecordMapper.class);
        identityProvider = mock(IdentityProvider.class);
        service = new AiWriteServiceImpl();
        setField(service, "writeRecordMapper", writeRecordMapper);
        setField(service, "identityProvider", identityProvider);
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("删除记录：按当前用户去重后批量删除，返回实际删除条数")
    void deleteOwnRecords()
    {
        when(writeRecordMapper.deleteWriteRecords(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.anyList())).thenReturn(2);

        int deleted = service.deleteWriteRecords(Arrays.asList(3L, null, 5L, 3L, 0L, -1L));

        assertEquals(2, deleted);
        verify(writeRecordMapper).deleteWriteRecords(USER_ID, List.of(3L, 5L));
    }

    @Test
    @DisplayName("删除记录：空入参或全是脏 ID 时不查库")
    void deleteEmptySkipsMapper()
    {
        assertEquals(0, service.deleteWriteRecords(null));
        assertEquals(0, service.deleteWriteRecords(List.of()));
        assertEquals(0, service.deleteWriteRecords(Arrays.asList(null, 0L, -8L)));
        verify(writeRecordMapper, never()).deleteWriteRecords(USER_ID, List.of());
    }

    @Test
    @DisplayName("删除记录：未登录时不删除任何记录")
    void deleteWithoutIdentity()
    {
        when(identityProvider.currentUserId()).thenReturn(null);

        assertEquals(0, service.deleteWriteRecords(List.of(1L, 2L)));
        verify(writeRecordMapper, never()).deleteWriteRecords(USER_ID, List.of(1L, 2L));
    }

    @Test
    @DisplayName("控制器：接受数字/字符串混排的 recordIds，忽略脏值后调用服务")
    void controllerParsesMixedIds()
    {
        IAiWriteService service = mock(IAiWriteService.class);
        when(service.deleteWriteRecords(List.of(3L, 5L))).thenReturn(2);
        AppAiController controller = new AppAiController(service);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("recordIds", Arrays.asList(3, "5", null, "abc", 0, -2, 3.5));

        AppApiResponse<Integer> response = controller.deleteWriteRecords(body);

        assertEquals(200, response.getCode());
        assertEquals(2, response.getData());
        verify(service).deleteWriteRecords(List.of(3L, 5L));
    }

    @Test
    @DisplayName("控制器：缺少 recordIds 时不报错，返回删除 0 条")
    void controllerToleratesMissingBody()
    {
        IAiWriteService service = mock(IAiWriteService.class);
        when(service.deleteWriteRecords(List.of())).thenReturn(0);
        AppAiController controller = new AppAiController(service);

        AppApiResponse<Integer> response = controller.deleteWriteRecords(null);

        assertEquals(200, response.getCode());
        assertEquals(0, response.getData());
        verify(service).deleteWriteRecords(List.of());
    }
}
