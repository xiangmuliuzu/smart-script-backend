package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.exception.AiQuotaInsufficientException;
import com.smartscript.platform.content.mapper.AppAiRequestMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * AI 次数不足校验单元测试（App / E 模块）。
 *
 * 验证写作/润色/大纲在调用外部 AI 前先校验 sys_ai_quota_account.available_quota：
 * 可用次数不足 1 次 → 抛 {@link AiQuotaInsufficientException}（控制器转 400，
 * App 端弹「AI 次数不足」引导），且不调用 AI 服务、不落任何记录。
 */
class AiQuotaCheckTest
{
    private static final Long USER_ID = 106L;

    private JdbcTemplate jdbcTemplate;
    private AppAiRequestMapper aiRequestMapper;
    private AppAiWriteRecordMapper writeRecordMapper;
    private AiServiceClient aiServiceClient;
    private IdentityProvider identityProvider;
    private AiWriteServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        jdbcTemplate = mock(JdbcTemplate.class);
        aiRequestMapper = mock(AppAiRequestMapper.class);
        writeRecordMapper = mock(AppAiWriteRecordMapper.class);
        aiServiceClient = mock(AiServiceClient.class);
        identityProvider = mock(IdentityProvider.class);
        service = new AiWriteServiceImpl();
        setField(service, "jdbcTemplate", jdbcTemplate);
        setField(service, "aiRequestMapper", aiRequestMapper);
        setField(service, "writeRecordMapper", writeRecordMapper);
        setField(service, "aiServiceClient", aiServiceClient);
        setField(service, "identityProvider", identityProvider);
        setField(service, "aiModel", "deepseek-flash");
        setField(service, "aiProvider", "deepseek");
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
    }

    @Test
    @DisplayName("可用次数为0：写作抛AI次数不足，且不调用AI服务")
    void write_quotaZero_throws() throws Exception
    {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(0);

        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("test");

        AiQuotaInsufficientException ex = assertThrows(AiQuotaInsufficientException.class,
                () -> service.write(request));
        assertTrue(ex.getMessage().contains("AI次数不足"));
        assertTrue(ex.getMessage().contains("0"));
        verify(aiServiceClient, never()).write(any(), any());
        verify(writeRecordMapper, never()).insertWriteRecord(any());
    }

    @Test
    @DisplayName("账户不存在（查询无结果）：视为0次，同样抛AI次数不足")
    void write_noAccount_throws() throws Exception
    {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("test");

        assertThrows(AiQuotaInsufficientException.class, () -> service.write(request));
        verify(aiServiceClient, never()).write(any(), any());
    }

    @Test
    @DisplayName("可用次数充足：写作正常调用AI并落记录")
    void write_quotaEnough_ok() throws Exception
    {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(5);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree("{\"content\":\"生成的内容\"}");
        when(aiServiceClient.write("test", null)).thenReturn(data);

        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("test");

        AppAiWriteResult result = service.write(request);
        assertEquals("生成的内容", result.getContent());
        verify(aiServiceClient, org.mockito.Mockito.times(1)).write("test", null);
        verify(writeRecordMapper, org.mockito.Mockito.times(1)).insertWriteRecord(any());
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
