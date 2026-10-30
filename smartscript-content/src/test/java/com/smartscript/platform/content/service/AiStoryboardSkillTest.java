package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.smartscript.platform.content.dto.AppAiOutlineRequest;
import com.smartscript.platform.content.dto.AppAiStoryboardRequest;
import com.smartscript.platform.content.dto.AppAiStoryboardResult;
import com.smartscript.platform.content.dto.AppAiWriteRecordInsert;
import com.smartscript.platform.content.exception.AiQuotaInsufficientException;
import com.smartscript.platform.content.mapper.AppAiRequestMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * E 模块：AI 分镜（剧本→分镜一条龙）+ 导演(skill) 注入 单元测试。
 *
 * 覆盖：
 * 1. storyboard：skillId 命中 → resolveSkillContent 内容注入 client；落 sys_ai_request
 *    （capability=storyboard）与 sys_ai_write_record（write_type=storyboard）；按 token 扣积分。
 * 2. storyboard 不带 skillId → skillContent 传 null，流程照常。
 * 3. 积分不足 → 抛 AiQuotaInsufficientException，不调用 AI 服务。
 * 4. storyboardStream（流式）成功落库扣积分。
 * 5. outline 携带 skillId → skill 内容注入剧本大纲调用。
 */
class AiStoryboardSkillTest
{
    private static final Long USER_ID = 106L;

    private AppAiRequestMapper aiRequestMapper;
    private AppAiWriteRecordMapper writeRecordMapper;
    private AiServiceClient aiServiceClient;
    private com.smartscript.platform.content.service.IAiSkillService aiSkillService;
    private com.smartscript.platform.review.service.RiskCheckService riskCheckService;
    private IdentityProvider identityProvider;
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private AiWriteServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        aiRequestMapper = mock(AppAiRequestMapper.class);
        writeRecordMapper = mock(AppAiWriteRecordMapper.class);
        aiServiceClient = mock(AiServiceClient.class);
        aiSkillService = mock(com.smartscript.platform.content.service.IAiSkillService.class);
        riskCheckService = mock(com.smartscript.platform.review.service.RiskCheckService.class);
        identityProvider = mock(IdentityProvider.class);
        jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        service = new AiWriteServiceImpl();
        setField(service, "aiRequestMapper", aiRequestMapper);
        setField(service, "writeRecordMapper", writeRecordMapper);
        setField(service, "aiServiceClient", aiServiceClient);
        setField(service, "aiSkillService", aiSkillService);
        setField(service, "riskCheckService", riskCheckService);
        setField(service, "identityProvider", identityProvider);
        setField(service, "jdbcTemplate", jdbcTemplate);
        setField(service, "aiModel", "deepseek-flash");
        setField(service, "aiProvider", "deepseek");
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
        // checkPoints：余额 5 积分（够 1 积分门槛）
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(5);
        // 扣积分：UPDATE（3参）成功 + 写积分流水（5参）成功
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("分镜：skillId 命中 → skill 内容注入 AI 调用，双写落库并扣积分")
    void storyboardWithSkillInjectsAndRecords() throws Exception
    {
        String script = "第一幕：雨夜，女主在便利店躲雨，遇见男主。";
        String skillContent = "你是悬疑烧脑导演，要求每镜埋一个线索。";
        when(aiSkillService.resolveSkillContent(1L)).thenReturn(skillContent);
        when(aiServiceClient.storyboard(eq(script), eq("电影感"), eq(skillContent)))
                .thenReturn(new AiCallResult("【第一场】便利店 夜\n镜1 全景...", 800, "deepseek-flash"));

        AppAiStoryboardRequest request = new AppAiStoryboardRequest();
        request.setScript(script);
        request.setStyle("电影感");
        request.setSkillId(1L);

        AppAiStoryboardResult result = service.storyboard(request);

        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("镜1"));
        assertEquals(800, result.getQuotaUsed());
        // 风控校验剧本内容
        verify(riskCheckService).checkContentOrThrow(USER_ID, script);
        // 元信息表：capability=storyboard
        verify(aiRequestMapper, times(1)).insertAiRequest(
                any(String.class), eq(USER_ID), nullable(Long.class),
                eq("storyboard"), eq(800), eq("success"), eq("deepseek"), any(String.class),
                any(java.util.Date.class), any(java.util.Date.class));
        // 可回看记录：write_type=storyboard，输入含剧本文本
        ArgumentCaptor<AppAiWriteRecordInsert> captor =
                ArgumentCaptor.forClass(AppAiWriteRecordInsert.class);
        verify(writeRecordMapper, times(1)).insertWriteRecord(captor.capture());
        AppAiWriteRecordInsert row = captor.getValue();
        assertEquals("storyboard", row.getWriteType());
        assertEquals(USER_ID, row.getUserId());
        assertTrue(row.getInputPrompt().contains(script));
        // 积分：UPDATE 扣减 + INSERT 流水各一次
        verify(jdbcTemplate, times(1)).update(anyString(), any(), any(), any());
        verify(jdbcTemplate, times(1)).update(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("分镜：不带 skillId → skillContent 传 null，流程照常")
    void storyboardWithoutSkillPassesNull() throws Exception
    {
        String script = "第二幕：两人在桥上重逢。";
        when(aiServiceClient.storyboard(eq(script), nullable(String.class), nullable(String.class)))
                .thenReturn(new AiCallResult("【第二场】桥上 日\n镜1 中景...", 300, "deepseek-flash"));

        AppAiStoryboardRequest request = new AppAiStoryboardRequest();
        request.setScript(script);

        AppAiStoryboardResult result = service.storyboard(request);
        assertNotNull(result.getContent());
        verify(aiServiceClient).storyboard(eq(script), nullable(String.class), nullable(String.class));
        // 无 skillId 时不做 skill 解析
        verify(aiSkillService, never()).resolveSkillContent(any(Long.class));
    }

    @Test
    @DisplayName("分镜：积分余额不足 → 抛积分不足，不调用 AI 服务、不落库")
    void storyboardQuotaZeroThrows() throws Exception
    {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(0);

        AppAiStoryboardRequest request = new AppAiStoryboardRequest();
        request.setScript("第三幕：真相揭开。");

        AiQuotaInsufficientException ex = assertThrows(AiQuotaInsufficientException.class,
                () -> service.storyboard(request));
        assertTrue(ex.getMessage().contains("积分不足"));
        verify(aiServiceClient, never()).storyboard(any(), any(), any());
        verify(aiRequestMapper, never()).insertAiRequest(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any(), any(), any(), any());
        verify(writeRecordMapper, never()).insertWriteRecord(any());
    }

    @Test
    @DisplayName("分镜（流式）：成功落库扣积分，并返回完整内容")
    void storyboardStreamWorksAndRecords() throws Exception
    {
        String script = "第四幕：女主决定离开。";
        String skillContent = "你是甜宠爆款导演，要求每镜突出心动细节。";
        when(aiSkillService.resolveSkillContent(2L)).thenReturn(skillContent);
        when(aiServiceClient.storyboardStream(eq(script), nullable(String.class), eq(skillContent), any()))
                .thenReturn(new AiCallResult("【第四场】车站 黄昏\n镜1 特写...", 500, "deepseek-flash"));

        AppAiStoryboardRequest request = new AppAiStoryboardRequest();
        request.setScript(script);
        request.setSkillId(2L);

        StringBuilder deltas = new StringBuilder();
        AppAiStoryboardResult result = service.storyboardStream(request, (Consumer<String>) deltas::append);

        assertTrue(result.getContent().contains("镜1"));
        assertEquals(500, result.getQuotaUsed());
        verify(aiServiceClient).storyboardStream(eq(script), nullable(String.class), eq(skillContent), any());
        verify(aiRequestMapper, times(1)).insertAiRequest(
                any(String.class), eq(USER_ID), nullable(Long.class),
                eq("storyboard"), eq(500), eq("success"), eq("deepseek"), any(String.class),
                any(java.util.Date.class), any(java.util.Date.class));
        ArgumentCaptor<AppAiWriteRecordInsert> captor =
                ArgumentCaptor.forClass(AppAiWriteRecordInsert.class);
        verify(writeRecordMapper, times(1)).insertWriteRecord(captor.capture());
        assertEquals("storyboard", captor.getValue().getWriteType());
    }

    @Test
    @DisplayName("剧本大纲：skillId 命中 → skill 内容注入 outline 调用")
    void outlineWithSkillInjectsSkillContent() throws Exception
    {
        String skillContent = "你是悬疑烧脑导演，大纲必须埋满线索。";
        when(aiSkillService.resolveSkillContent(1L)).thenReturn(skillContent);
        String outlineJson = "{\"outline\":{\"title\":\"雨夜迷局\",\"logline\":\"一场雨夜偶遇引出连环谜案\"}}";
        when(aiServiceClient.outline(eq("雨夜便利店"), eq("悬疑"), eq("烧脑"), eq(skillContent)))
                .thenReturn(new AiCallResult(outlineJson, 400, "deepseek-flash"));

        AppAiOutlineRequest request = new AppAiOutlineRequest();
        request.setInspiration("雨夜便利店");
        request.setGenre("悬疑");
        request.setStyle("烧脑");
        request.setSkillId(1L);

        com.smartscript.platform.content.dto.AppAiOutlineResult result = service.outline(request);

        assertNotNull(result.getOutline());
        verify(aiServiceClient).outline(eq("雨夜便利店"), eq("悬疑"), eq("烧脑"), eq(skillContent));
        verify(aiRequestMapper, times(1)).insertAiRequest(
                any(String.class), eq(USER_ID), nullable(Long.class),
                eq("outline"), eq(400), eq("success"), eq("deepseek"), any(String.class),
                any(java.util.Date.class), any(java.util.Date.class));
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
