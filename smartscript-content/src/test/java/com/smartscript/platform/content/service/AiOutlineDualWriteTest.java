package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.nullable;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.content.dto.AppAiOutlineRequest;
import com.smartscript.platform.content.dto.AppAiOutlineResult;
import com.smartscript.platform.content.dto.AppAiWriteRecordInsert;
import com.smartscript.platform.content.mapper.AppAiRequestMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.service.AiServiceClient;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * AI 大纲双写单元测试（App / E 模块）。
 *
 * 验证 outline() 除了落 sys_ai_request 元信息外，还同步写一条
 * sys_ai_write_record（write_type='outline'，输入/输出完整），
 * 使 AI 记录页可像写作/润色一样回看大纲输入与结果。
 */
class AiOutlineDualWriteTest
{
    private static final Long USER_ID = 106L;

    private AppAiRequestMapper aiRequestMapper;
    private AppAiWriteRecordMapper writeRecordMapper;
    private AiServiceClient aiServiceClient;
    private IdentityProvider identityProvider;
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private AiWriteServiceImpl service;
    private com.smartscript.platform.review.service.RiskCheckService riskCheckService;

    @BeforeEach
    void setUp() throws Exception
    {
        aiRequestMapper = mock(AppAiRequestMapper.class);
        writeRecordMapper = mock(AppAiWriteRecordMapper.class);
        aiServiceClient = mock(AiServiceClient.class);
        identityProvider = mock(IdentityProvider.class);
        jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        service = new AiWriteServiceImpl();
        riskCheckService = mock(com.smartscript.platform.review.service.RiskCheckService.class);
        setField(service, "riskCheckService", riskCheckService);
        setField(service, "aiRequestMapper", aiRequestMapper);
        setField(service, "writeRecordMapper", writeRecordMapper);
        setField(service, "aiServiceClient", aiServiceClient);
        setField(service, "identityProvider", identityProvider);
        setField(service, "jdbcTemplate", jdbcTemplate);
        setField(service, "aiModel", "deepseek-flash");
        setField(service, "aiProvider", "deepseek");
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(5);
    }

    @Test
    @DisplayName("大纲生成：除请求元信息外，同步写一条 write_type=outline 的可回看记录（含输入/输出）")
    void outlineWritesDualRecord() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree(
            "{\"outline\":{\"title\":\"都市逆袭\",\"logline\":\"平凡青年逆袭登顶\",\"acts\":[\"跌入谷底\",\"登顶巅峰\"]},\"model\":\"deepseek-flash\"}");
        when(aiServiceClient.outline("都市青年逆袭", "都市", "爽文")).thenReturn(data);

        AppAiOutlineRequest request = new AppAiOutlineRequest();
        request.setInspiration("都市青年逆袭");
        request.setGenre("都市");
        request.setStyle("爽文");

        AppAiOutlineResult result = service.outline(request);
        verify(riskCheckService).checkContentOrThrow(USER_ID, "都市青年逆袭");

        // 元信息表照旧落一条
        verify(aiRequestMapper, times(1)).insertAiRequest(
            any(String.class), eq(USER_ID), nullable(Long.class),
            eq("outline"), eq(1), eq("success"), eq("deepseek"), any(String.class),
            any(java.util.Date.class), any(java.util.Date.class));

        // 可回看记录落一条，字段按契约
        ArgumentCaptor<AppAiWriteRecordInsert> captor =
            ArgumentCaptor.forClass(AppAiWriteRecordInsert.class);
        verify(writeRecordMapper, times(1)).insertWriteRecord(captor.capture());
        AppAiWriteRecordInsert row = captor.getValue();
        assertEquals("outline", row.getWriteType());
        assertEquals(USER_ID, row.getUserId());
        assertTrue(row.getInputPrompt().contains("灵感：都市青年逆袭"));
        assertTrue(row.getInputPrompt().contains("类型：都市"));
        assertTrue(row.getInputPrompt().contains("风格：爽文"));
        assertTrue(row.getOutputContent().contains("书名：都市逆袭"));
        assertTrue(row.getOutputContent().contains("一句话简介"));
        assertTrue(row.getOutputContent().contains("跌入谷底"));
        assertFalse(row.getOutputContent().contains("{\"title\""));
        assertEquals("deepseek-flash", row.getAiModel());
        assertEquals("success", row.getStatus());

        assertNotNull(result.getRequestNo());
        assertTrue(!result.getRequestNo().isEmpty());
        assertNotNull(result.getOutline());
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
