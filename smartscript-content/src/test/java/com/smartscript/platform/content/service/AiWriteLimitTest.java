package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import com.smartscript.platform.content.dto.AppAiWriteRecordInsert;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.mapper.AppAiRequestMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * AI 生成字数上限截断集成测试（防平台亏损）。
 *
 * 验证流式写作/润色：AI 输出超限时，转发给前端与落库的内容都被截断到上限，
 * 返回结果带 truncated=true；未超限时不截断。上限 0 表示不限。
 */
class AiWriteLimitTest
{
    private static final Long USER_ID = 106L;

    private JdbcTemplate jdbcTemplate;
    private AppAiRequestMapper aiRequestMapper;
    private AppAiWriteRecordMapper writeRecordMapper;
    private AiServiceClient aiServiceClient;
    private IdentityProvider identityProvider;
    private AiGenLimitConfig genLimitConfig;
    private AiWriteServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        jdbcTemplate = mock(JdbcTemplate.class);
        aiRequestMapper = mock(AppAiRequestMapper.class);
        writeRecordMapper = mock(AppAiWriteRecordMapper.class);
        aiServiceClient = mock(AiServiceClient.class);
        identityProvider = mock(IdentityProvider.class);
        genLimitConfig = mock(AiGenLimitConfig.class);
        service = new AiWriteServiceImpl();
        setField(service, "jdbcTemplate", jdbcTemplate);
        setField(service, "aiRequestMapper", aiRequestMapper);
        setField(service, "writeRecordMapper", writeRecordMapper);
        setField(service, "aiServiceClient", aiServiceClient);
        setField(service, "identityProvider", identityProvider);
        setField(service, "genLimitConfig", genLimitConfig);
        setField(service, "aiModel", "deepseek-flash");
        setField(service, "aiProvider", "deepseek");
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(5);
        when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(1);
    }

    @Test
    @DisplayName("流式写作超限：前端只收到上限字符，落库截断，truncated=true")
    void writeStream_overLimit_truncated() throws Exception
    {
        when(genLimitConfig.maxChars("write")).thenReturn(5);
        String full = "0123456789"; // 10 字符 > 上限 5
        when(aiServiceClient.writeStream(anyString(), any(), any())).thenAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(2);
            for (int i = 0; i < full.length(); i += 2)
            {
                onDelta.accept(full.substring(i, Math.min(i + 2, full.length())));
            }
            return full;
        });

        List<String> received = new ArrayList<>();
        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("请写一段故事");
        AppAiWriteResult result = service.writeStream(request, received::add);

        assertEquals("01234", String.join("", received), "前端只应收到前 5 个字符");
        assertEquals("01234", result.getContent(), "返回内容应截断到 5");
        assertTrue(result.isTruncated());

        ArgumentCaptor<AppAiWriteRecordInsert> captor = ArgumentCaptor.forClass(AppAiWriteRecordInsert.class);
        verify(writeRecordMapper).insertWriteRecord(captor.capture());
        assertEquals("01234", captor.getValue().getOutputContent(), "落库内容应截断到 5");
    }

    @Test
    @DisplayName("流式写作未超限：全量转发，truncated=false")
    void writeStream_belowLimit_noTruncate() throws Exception
    {
        when(genLimitConfig.maxChars("write")).thenReturn(100);
        String full = "一段正常长度的内容";
        when(aiServiceClient.writeStream(anyString(), any(), any())).thenAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(2);
            onDelta.accept(full);
            return full;
        });

        List<String> received = new ArrayList<>();
        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("请写一段故事");
        AppAiWriteResult result = service.writeStream(request, received::add);

        assertEquals(full, String.join("", received));
        assertEquals(full, result.getContent());
        assertFalse(result.isTruncated());
    }

    @Test
    @DisplayName("上限0（不限）：全量转发，不截断")
    void writeStream_unlimited_noTruncate() throws Exception
    {
        when(genLimitConfig.maxChars("write")).thenReturn(0);
        String full = "不限长度时应当完整返回的内容";
        when(aiServiceClient.writeStream(anyString(), any(), any())).thenAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(2);
            onDelta.accept(full);
            return full;
        });

        List<String> received = new ArrayList<>();
        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("请写一段故事");
        AppAiWriteResult result = service.writeStream(request, received::add);

        assertEquals(full, String.join("", received));
        assertEquals(full, result.getContent());
        assertFalse(result.isTruncated());
    }

    @Test
    @DisplayName("同步写作超限：返回与落库内容截断，truncated=true")
    void write_sync_overLimit_truncated() throws Exception
    {
        when(genLimitConfig.maxChars("write")).thenReturn(3);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode data = mapper.readTree("{\"content\":\"abcdef\"}");
        when(aiServiceClient.write("test", null)).thenReturn(data);

        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("test");
        AppAiWriteResult result = service.write(request);

        assertEquals("abc", result.getContent());
        assertTrue(result.isTruncated());
        ArgumentCaptor<AppAiWriteRecordInsert> captor = ArgumentCaptor.forClass(AppAiWriteRecordInsert.class);
        verify(writeRecordMapper).insertWriteRecord(captor.capture());
        assertEquals("abc", captor.getValue().getOutputContent());
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
