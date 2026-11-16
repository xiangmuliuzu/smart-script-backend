package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.content.dto.AppAiOutlineRecordItem;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.mapper.AppAiRequestMapper;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * AI 大纲请求记录分页服务单元测试（App / E 模块）。
 *
 * 不连库：mapper 与身份均 mock。PageHelper.startPage 在无 MyBatis 拦截器时
 * 不影响 mock 返回，PageInfo 对普通 List 取 total=size。
 */
class AiOutlineRecordServiceTest
{
    private static final Long USER_ID = 106L;

    private AppAiRequestMapper aiRequestMapper;
    private IdentityProvider identityProvider;
    private AiWriteServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        aiRequestMapper = mock(AppAiRequestMapper.class);
        identityProvider = mock(IdentityProvider.class);
        service = new AiWriteServiceImpl();
        setField(service, "aiRequestMapper", aiRequestMapper);
        setField(service, "identityProvider", identityProvider);
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
    }

    private static AppAiOutlineRecordItem item(long requestId, String requestNo)
    {
        AppAiOutlineRecordItem item = new AppAiOutlineRecordItem();
        item.setRequestId(requestId);
        item.setRequestNo(requestNo);
        item.setCapability("outline");
        item.setQuotaCost(1);
        item.setStatus("success");
        item.setProvider("deepseek");
        return item;
    }

    @Test
    @DisplayName("大纲记录：返回当前登录用户的记录，total=条数，并以当前身份查询")
    void pageOutlineRecordsReturnsOwn()
    {
        List<AppAiOutlineRecordItem> rows = List.of(item(15L, "REQ15"), item(14L, "REQ14"));
        when(aiRequestMapper.selectOutlineRecordsByUser(USER_ID)).thenReturn(rows);

        AppPageResult<AppAiOutlineRecordItem> result = service.pageOutlineRecords(1, 20);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals(15L, result.getList().get(0).getRequestId());
        assertEquals("REQ15", result.getList().get(0).getRequestNo());
        verify(aiRequestMapper).selectOutlineRecordsByUser(USER_ID);
    }

    @Test
    @DisplayName("大纲记录：无记录时 total=0、list 为空")
    void pageOutlineRecordsEmpty()
    {
        when(aiRequestMapper.selectOutlineRecordsByUser(USER_ID)).thenReturn(List.of());

        AppPageResult<AppAiOutlineRecordItem> result = service.pageOutlineRecords(1, 20);

        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(aiRequestMapper).selectOutlineRecordsByUser(USER_ID);
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
