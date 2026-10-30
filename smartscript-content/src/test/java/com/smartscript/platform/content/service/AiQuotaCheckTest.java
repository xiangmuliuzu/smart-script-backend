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
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.exception.AiQuotaInsufficientException;
import com.smartscript.platform.content.mapper.AppAiRequestMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 【去次数化】AI 创作积分校验单元测试（App / E 模块）。
 *
 * 验证写作/润色/大纲在调用外部 AI 前先校验 sys_points_account.balance：
 * 积分不足 1 分 → 抛 {@link AiQuotaInsufficientException}（控制器转 400，
 * App 端弹「积分不足」引导），且不调用 AI 服务、不落任何记录。
 * AI 调用成功后按实际 token 消耗扣减积分（1积分=N token，向上取整，最低1积分）。
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
        setField(service, "aiSkillService",
                mock(com.smartscript.platform.content.service.IAiSkillService.class));
        setField(service, "identityProvider", identityProvider);
        setField(service, "aiModel", "deepseek-flash");
        setField(service, "aiProvider", "deepseek");
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
    }

    @Test
    @DisplayName("积分余额为0：写作抛积分不足，且不调用AI服务")
    void write_quotaZero_throws() throws Exception
    {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(0);

        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("test");

        AiQuotaInsufficientException ex = assertThrows(AiQuotaInsufficientException.class,
                () -> service.write(request));
        assertTrue(ex.getMessage().contains("积分不足"));
        assertTrue(ex.getMessage().contains("0"));
        verify(aiServiceClient, never()).write(any(), any(), any());
        verify(writeRecordMapper, never()).insertWriteRecord(any());
    }

    @Test
    @DisplayName("账户不存在（查询无结果）：视为0积分，同样抛积分不足")
    void write_noAccount_throws() throws Exception
    {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("test");

        assertThrows(AiQuotaInsufficientException.class, () -> service.write(request));
        verify(aiServiceClient, never()).write(any(), any(), any());
    }

    @Test
    @DisplayName("积分充足：写作正常调用AI并落记录")
    void write_quotaEnough_ok() throws Exception
    {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(100);
        when(aiServiceClient.write(anyString(), org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(new AiCallResult("生成的内容", 150, "deepseek-flash"));

        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("test");

        AppAiWriteResult result = service.write(request);
        assertEquals("生成的内容", result.getContent());
        verify(aiServiceClient, org.mockito.Mockito.times(1))
                .write(anyString(), org.mockito.ArgumentMatchers.nullable(String.class),
                        org.mockito.ArgumentMatchers.nullable(String.class));
        verify(writeRecordMapper, org.mockito.Mockito.times(1)).insertWriteRecord(any());
    }

    @Test
    @DisplayName("AI调用成功后按token扣积分并写积分流水（SPEND/AI）")
    void write_success_deductsPoints() throws Exception
    {
        // 积分余额查询（checkPoints 与扣减后的 after 查询均返回 100）
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(100);
        // UPDATE（扣减，3个参数）与 INSERT（流水，5个参数）均成功
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        when(aiServiceClient.write(anyString(), org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(new AiCallResult("生成的内容", 1500, "deepseek-flash"));

        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt("test");
        AppAiWriteResult result = service.write(request);
        assertEquals("生成的内容", result.getContent());

        // 两次 jdbcTemplate.update：一次 UPDATE 扣积分（args=3），一次 INSERT 积分流水（args=5）
        org.mockito.ArgumentCaptor<Object[]> captor = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(anyString(), (Object[]) captor.capture());
        java.util.List<Object[]> allArgs = captor.getAllValues();
        long updateCount = allArgs.stream().filter(a -> a.length == 3).count();
        long insertCount = allArgs.stream().filter(a -> a.length == 5).count();
        assertEquals(1, updateCount, "应执行1次扣减UPDATE");
        assertEquals(1, insertCount, "应写入1条积分流水");
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
