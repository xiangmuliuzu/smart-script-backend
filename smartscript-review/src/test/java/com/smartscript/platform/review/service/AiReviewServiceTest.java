package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.AiReviewRule;
import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.mapper.AiReviewRuleMapper;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AI审核规则服务核心逻辑单元测试
 * 覆盖：提示词生成 / Mock确定性打分 / 规则命中扣分 / 主流程落库
 */
@ExtendWith(MockitoExtension.class)
class AiReviewServiceTest {

    @Mock
    private AiReviewRuleMapper aiReviewRuleMapper;

    @Mock
    private ReviewRecordMapper reviewRecordMapper;

    @InjectMocks
    private AiReviewService aiReviewService;

    private AiReviewRule contentRule;
    private AiReviewRule sensitiveRule;

    @BeforeEach
    void setUp() {
        contentRule = new AiReviewRule();
        contentRule.setRuleId(1L);
        contentRule.setRuleName("暴力内容检测");
        contentRule.setRuleType("content_safety");
        contentRule.setRuleContent("violence");
        contentRule.setThreshold(80);
        contentRule.setAction("auto_reject");

        sensitiveRule = new AiReviewRule();
        sensitiveRule.setRuleId(4L);
        sensitiveRule.setRuleName("敏感词命中");
        sensitiveRule.setRuleType("sensitive_word");
        sensitiveRule.setRuleContent("违禁,非法");
        sensitiveRule.setThreshold(90);
    }

    @Test
    void buildPrompt_有规则_包含规则名与阈值() {
        String prompt = aiReviewService.buildPrompt(Collections.singletonList(contentRule));
        assertTrue(prompt.contains("暴力内容检测"));
        assertTrue(prompt.contains("80"));
        assertTrue(prompt.contains("auto_reject"));
    }

    @Test
    void buildPrompt_无规则_提示用通用标准() {
        String prompt = aiReviewService.buildPrompt(Collections.emptyList());
        assertTrue(prompt.contains("暂无审核规则"));
    }

    @Test
    void mockScore_正常内容_满分低风险() {
        Map<String, Object> r = aiReviewService.mockScore("这是一篇优秀的悬疑小说，情节紧凑", Collections.singletonList(contentRule));
        assertEquals(100, r.get("score"));
        assertEquals("low", r.get("level"));
    }

    @Test
    void mockScore_返回AI侧基线100_词库扣分由score处理() {
        // 新逻辑：mockScore 仅返回 AI 侧基线，词库命中确定性扣分在 score() 中处理，避免重复扣分
        Map<String, Object> r = aiReviewService.mockScore("他拿起刀，用暴力解决了一切", Collections.singletonList(contentRule));
        assertEquals(100, r.get("score"));
        assertEquals("low", r.get("level"));
        assertTrue(((java.util.List<?>) r.get("keywords")).isEmpty());
    }

    @Test
    void mockScore_无违规_issues为空() {
        Map<String, Object> r = aiReviewService.mockScore("这是一篇优秀的悬疑小说，情节紧凑", Collections.singletonList(contentRule));
        assertEquals(100, r.get("score"));
        java.util.List<Map<String, Object>> issues = (java.util.List<Map<String, Object>>) r.get("issues");
        assertTrue(issues == null || issues.isEmpty());
    }

    @Test
    void score_词库命中_确定性扣分_不依赖AI分() {
        // 核心新逻辑：规则配置违禁词词库后，命中词库即确定性扣分（暴力2字-15、血腥2字-15 -> 70分），不再全凭AI自主判断
        contentRule.setRuleValue("{\"words\":[\"暴力\",\"血腥\"]}");
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(50L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(50L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(contentRule));

        Map<String, Object> r = aiReviewService.score(50L, "他拿起刀，用暴力解决了一切，场面血腥");
        assertEquals(200, r.get("code"));
        assertEquals(70, ((Number) r.get("aiScore")).intValue());
        assertTrue(((java.util.List<?>) r.get("keywords")).contains("暴力"));
        assertTrue(((java.util.List<?>) r.get("keywords")).contains("血腥"));
    }

    @Test
    void score_词库命中_生成原句引用() {
        // 词库命中的问题引用必须是正文原句（供审核工作台/用户定位）
        contentRule.setRuleValue("{\"words\":[\"暴力\"]}");
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(51L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(51L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(contentRule));

        Map<String, Object> r = aiReviewService.score(51L, "他拿起刀，用暴力解决了一切。大家都吓坏了");
        org.mockito.ArgumentCaptor<ReviewRecord> captor = org.mockito.ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordMapper).updateReviewRecord(captor.capture());
        assertTrue(captor.getValue().getAiResult().contains("他拿起刀，用暴力解决了一切"));
        assertTrue(captor.getValue().getAiResult().contains("命中违禁词词库"));
    }

    @Test
    void decideAction_有词库_命中词或AI低分_均触发() {
        // 词库是辅助：命中词库词即触发（即使AI综合分100）；AI是必要：词库未命中但AI分<阈值也触发
        contentRule.setRuleValue("{\"words\":[\"暴力\"]}");
        assertEquals("auto_reject", aiReviewService.decideAction(100, "他使用暴力解决问题", Collections.singletonList(contentRule)));
        assertEquals("auto_reject", aiReviewService.decideAction(0, "这是一篇正常的武侠小说", Collections.singletonList(contentRule)));
        assertNull(aiReviewService.decideAction(90, "这是一篇正常的武侠小说", Collections.singletonList(contentRule)));
    }

    @Test
    void decideAction_无词库_按AI分阈值判定() {
        // 版权查重等无词库规则（rule_value 为非JSON占位值）：仍按 AI 综合分 < 阈值 判定
        contentRule.setRuleValue("plagiarism_check");
        assertEquals("auto_reject", aiReviewService.decideAction(60, "内容", Collections.singletonList(contentRule)));
        assertNull(aiReviewService.decideAction(90, "内容", Collections.singletonList(contentRule)));
    }

    @Test
    void score_词库未命中_AI低分仍按AI判定触发() {
        // 词库是辅助、AI是必要：规则配词库但正文未命中词库词时，
        // AI 自主打低分（40分 < 阈值80）仍会拉低最终分并触发规则 -> 自动驳回
        contentRule.setRuleValue("{\"words\":[\"暴力\",\"血腥\"]}");
        ReflectionTestUtils.setField(aiReviewService, "aiApiKey", "sk-test");
        ReflectionTestUtils.setField(aiReviewService, "aiBaseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(aiReviewService, "aiModel", "deepseek-chat");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(aiReviewService, "restTemplate", mockRest);
        String aiResp = "{\"choices\":[{\"message\":{\"content\":\"{\\\"score\\\":40,\\\"level\\\":\\\"high\\\","
                + "\\\"keywords\\\":[\\\"疑似暴力描写\\\"]}\"}}]}";
        when(mockRest.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResp, HttpStatus.OK));

        ReviewRecord record = new ReviewRecord();
        record.setReviewId(60L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(60L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(contentRule));

        Map<String, Object> r = aiReviewService.score(60L, "这是一篇优秀的悬疑小说，情节紧凑引人入胜");
        // 词库未命中 wordScore=100；min(100,40)=40 -> high -> 40<80 触发 contentRule(auto_reject) -> rejected
        assertEquals(40, ((Number) r.get("aiScore")).intValue());
        assertEquals("high", r.get("riskLevel"));
        assertEquals("rejected", r.get("nextStatus"));
    }

    @Test
    void score_存在无词库规则_AI低分参与取最严_版权规则触发() {
        // 版权查重（无词库）在场时，AI 语义判定仍参与：AI 40分 < 版权阈值80 -> 触发 -> 按处理方式拦截
        contentRule.setRuleValue("{\"words\":[\"暴力\",\"血腥\"]}");        AiReviewRule copyright = new AiReviewRule();
        copyright.setRuleId(2L);
        copyright.setRuleName("版权查重");
        copyright.setRuleType("copyright");
        copyright.setRuleContent("plagiarism_check");
        copyright.setRuleValue("plagiarism_check");
        copyright.setThreshold(80);
        copyright.setAction("auto_reject");
        ReflectionTestUtils.setField(aiReviewService, "aiApiKey", "sk-test");
        ReflectionTestUtils.setField(aiReviewService, "aiBaseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(aiReviewService, "aiModel", "deepseek-chat");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(aiReviewService, "restTemplate", mockRest);
        String aiResp = "{\"choices\":[{\"message\":{\"content\":\"{\\\"score\\\":40,\\\"level\\\":\\\"high\\\","
                + "\\\"keywords\\\":[\\\"疑似抄袭\\\"]}\"}}]}";
        when(mockRest.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResp, HttpStatus.OK));

        ReviewRecord record = new ReviewRecord();
        record.setReviewId(61L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(61L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Arrays.asList(contentRule, copyright));

        Map<String, Object> r = aiReviewService.score(61L, "这是一篇优秀的悬疑小说，情节紧凑引人入胜");
        // 词库未命中 wordScore=100；有版权规则 -> min(100,40)=40 -> high -> 版权 auto_reject -> rejected
        assertEquals(40, ((Number) r.get("aiScore")).intValue());
        assertEquals("high", r.get("riskLevel"));
        assertEquals("rejected", r.get("nextStatus"));
    }

    @Test
    void score_审核记录不存在_返回404() {
        when(reviewRecordMapper.selectReviewRecordById(99L)).thenReturn(null);
        assertEquals(404, aiReviewService.score(99L, "内容").get("code"));
    }

    @Test
    void score_主流程_打分并落库() {
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(12L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(12L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Arrays.asList(contentRule, sensitiveRule));

        Map<String, Object> r = aiReviewService.score(12L, "这是一篇正常作品，没有违规内容");
        assertEquals(200, r.get("code"));
        assertNotNull(r.get("aiScore"));
        verify(reviewRecordMapper, times(1)).updateReviewRecord(any(ReviewRecord.class));
    }

    @Test
    void score_有ApiKey_真调DeepSeek并解析JSON() {
        // 给定：配置了 api-key，mock RestTemplate 返回标准 DeepSeek 响应（content 带 markdown code fence）
        ReflectionTestUtils.setField(aiReviewService, "aiApiKey", "sk-test");
        ReflectionTestUtils.setField(aiReviewService, "aiBaseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(aiReviewService, "aiModel", "deepseek-chat");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(aiReviewService, "restTemplate", mockRest);

        // 合法JSON：choices[0].message.content 是字符串，内容为 ```json\n{...}\n```
        String aiResp = "{\"choices\":[{\"message\":{\"content\":\"```json\\n"
                + "{\\\"score\\\":92,\\\"level\\\":\\\"low\\\",\\\"keywords\\\":[]}\\n```\"}}]}";
        when(mockRest.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResp, HttpStatus.OK));

        ReviewRecord record = new ReviewRecord();
        record.setReviewId(20L);
        when(reviewRecordMapper.selectReviewRecordById(20L)).thenReturn(record);
        // 存在无词库规则（版权查重，AI分92>=阈值90不触发），AI分参与最终分 -> min(100,92)=92
        AiReviewRule copyright = new AiReviewRule();
        copyright.setRuleId(2L);
        copyright.setRuleName("版权查重");
        copyright.setRuleType("copyright");
        copyright.setRuleContent("plagiarism_check");
        copyright.setRuleValue("plagiarism_check");
        copyright.setThreshold(90);
        copyright.setAction("auto_reject");
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(copyright));

        Map<String, Object> r = aiReviewService.score(20L, "正常内容");
        assertEquals(200, r.get("code"));
        assertEquals(false, r.get("usingMock"));
        assertEquals(92, r.get("aiScore"));
        assertEquals("low", r.get("riskLevel"));
        verify(mockRest, times(1)).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    void score_外部AI调用失败_回退Mock() {
        ReflectionTestUtils.setField(aiReviewService, "aiApiKey", "sk-test");
        ReflectionTestUtils.setField(aiReviewService, "aiBaseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(aiReviewService, "aiModel", "deepseek-chat");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(aiReviewService, "restTemplate", mockRest);
        when(mockRest.postForEntity(any(String.class), any(), eq(String.class)))
                .thenThrow(new RuntimeException("timeout"));

        ReviewRecord record = new ReviewRecord();
        record.setReviewId(21L);
        when(reviewRecordMapper.selectReviewRecordById(21L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.emptyList());

        // 正常内容走 mock 得满分 100，证明回退成功
        Map<String, Object> r = aiReviewService.score(21L, "这是一篇优秀的悬疑小说，情节紧凑");
        assertEquals(200, r.get("code"));
        assertEquals(100, r.get("aiScore"));
    }

    @Test
    void score_低风险高分_直接通过审核_不再人工复审() {
        // AI打分 >=90：直接 approved（不再进人工复审 pending），并触发作品自动上架
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(30L);
        record.setStatus("ai_reviewing");
        record.setTargetType("work");
        record.setTargetId(301L);
        when(reviewRecordMapper.selectReviewRecordById(30L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.emptyList());

        Map<String, Object> r = aiReviewService.score(30L, "这是一篇优秀的悬疑小说，情节紧凑");
        assertEquals("approved", r.get("nextStatus"));
        org.mockito.ArgumentCaptor<ReviewRecord> captor = org.mockito.ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordMapper).updateReviewRecord(captor.capture());
        assertEquals("approved", captor.getValue().getStatus());
        verify(reviewRecordMapper).markWorkOnShelf(301L, null);
    }

    @Test
    void score_高风险_自动流转rejected() {
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(31L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(31L)).thenReturn(record);
        contentRule.setRuleValue("{\"words\":[\"暴力\",\"血腥\",\"色情\",\"毒品\",\"赌博\"]}");
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(contentRule));

        // 内容命中词库5个词：100-15*5=25 -> high -> 自动驳回
        Map<String, Object> r = aiReviewService.score(31L, "他拿起刀，用暴力血腥的色情毒品赌博手段解决一切");
        assertEquals("rejected", r.get("nextStatus"));
        org.mockito.ArgumentCaptor<ReviewRecord> captor = org.mockito.ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordMapper).updateReviewRecord(captor.capture());
        assertEquals("rejected", captor.getValue().getStatus());
    }

    @Test
    void score_状态已非ai_reviewing_不自动流转() {
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(32L);
        record.setStatus("pending"); // 已被人工改为 pending
        when(reviewRecordMapper.selectReviewRecordById(32L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.emptyList());

        Map<String, Object> r = aiReviewService.score(32L, "正常内容");
        assertEquals("pending", r.get("nextStatus"));
        org.mockito.ArgumentCaptor<ReviewRecord> captor = org.mockito.ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordMapper).updateReviewRecord(captor.capture());
        // upd 不应该带 status 字段（null）
        assertNull(captor.getValue().getStatus());
    }

    // ===== 处理方式（action）生效验证 =====

    @Test
    void score_规则处理方式人工复审_词库命中_流转pending() {
        // 核心修复：规则处理方式选「人工复审」，词库命中扣到90分以下（85<90），必须进人工复审，不得高分直通
        contentRule.setAction("manual_review");
        contentRule.setRuleValue("{\"words\":[\"暴力\"]}");
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(40L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(40L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(contentRule));

        // 含暴力词：词库命中 wordScore=85 < 90 -> 不直通，manual_review -> pending
        Map<String, Object> r = aiReviewService.score(40L, "他拿起刀，用暴力解决了一切");
        assertEquals("pending", r.get("nextStatus"));
        org.mockito.ArgumentCaptor<ReviewRecord> captor = org.mockito.ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordMapper).updateReviewRecord(captor.capture());
        assertEquals("pending", captor.getValue().getStatus());
    }

    @Test
    void score_规则处理方式人工复审_未命中_高分直通approved() {
        // 未命中规则且 AI 高分（100>=90）：直接通过审核，不再进人工复审
        contentRule.setAction("manual_review");
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(41L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(41L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(contentRule));

        Map<String, Object> r = aiReviewService.score(41L, "这是一篇优秀的悬疑小说，情节紧凑");
        assertEquals("approved", r.get("nextStatus"));
    }

    @Test
    void score_规则处理方式自动驳回_词库命中_流转rejected() {
        contentRule.setAction("auto_reject");
        contentRule.setRuleValue("{\"words\":[\"暴力\"]}");
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(42L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(42L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(contentRule));

        Map<String, Object> r = aiReviewService.score(42L, "他拿起刀，用暴力解决了一切");
        assertEquals("rejected", r.get("nextStatus"));
        org.mockito.ArgumentCaptor<ReviewRecord> captor = org.mockito.ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordMapper).updateReviewRecord(captor.capture());
        assertEquals("rejected", captor.getValue().getStatus());
    }

    @Test
    void score_规则处理方式自动通过_高分直通approved() {
        // auto_pass 命中=放行（不拦截）；AI 高分100>=90 -> 直接通过审核
        contentRule.setAction("auto_pass");
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(43L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(43L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(contentRule));

        Map<String, Object> r = aiReviewService.score(43L, "他拿起刀，用暴力解决了一切");
        assertEquals("approved", r.get("nextStatus"));
    }

    @Test
    void score_敏感词规则人工复审_词表命中_流转pending() {
        // 敏感词命中不依赖 AI 综合分，命中词表即触发 -> manual_review -> pending
        sensitiveRule.setAction("manual_review");
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(44L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(44L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(sensitiveRule));

        Map<String, Object> r = aiReviewService.score(44L, "内容里出现了违禁词汇");
        assertEquals("pending", r.get("nextStatus"));
    }

    @Test
    void score_多规则命中_取最严处置_自动驳回优先() {
        // content_safety 命中(score<threshold) 且 action=manual_review；sensitive_word 命中 且 action=auto_reject
        // 最严 = auto_reject -> rejected
        contentRule.setAction("manual_review");
        sensitiveRule.setAction("auto_reject");
        ReviewRecord record = new ReviewRecord();
        record.setReviewId(45L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(45L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Arrays.asList(contentRule, sensitiveRule));

        Map<String, Object> r = aiReviewService.score(45L, "他拿起刀用暴力手段，内容里还出现了违禁词汇");
        assertEquals("rejected", r.get("nextStatus"));
    }

    @Test
    void mockScore_基线100_无issues() {
        // mockScore 仅提供AI侧基线，词库扣分在 score() 中处理
        Map<String, Object> r = aiReviewService.mockScore("他拿起刀，用暴力解决了一切。大家都吓坏了", Collections.singletonList(contentRule));
        assertEquals(100, r.get("score"));
        java.util.List<Map<String, Object>> issues = (java.util.List<Map<String, Object>>) r.get("issues");
        assertTrue(issues == null || issues.isEmpty());
    }

    @Test
    void score_真调AI_解析issues并落库() {
        ReflectionTestUtils.setField(aiReviewService, "aiApiKey", "sk-test");
        ReflectionTestUtils.setField(aiReviewService, "aiBaseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(aiReviewService, "aiModel", "deepseek-chat");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(aiReviewService, "restTemplate", mockRest);

        // AI 返回带 issues 的 JSON（quote 为正文原文引用）
        String aiResp = "{\"choices\":[{\"message\":{\"content\":\"{\\\"score\\\":35,\\\"level\\\":\\\"high\\\","
                + "\\\"keywords\\\":[\\\"暴力描写\\\"],"
                + "\\\"issues\\\":[{\\\"reason\\\":\\\"暴力描写\\\",\\\"quote\\\":\\\"他拿起刀，用暴力解决了一切\\\"}]"
                + "}\"}}]}";
        when(mockRest.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResp, HttpStatus.OK));

        ReviewRecord record = new ReviewRecord();
        record.setReviewId(22L);
        when(reviewRecordMapper.selectReviewRecordById(22L)).thenReturn(record);
        // 存在无词库规则（版权查重）时 AI 分参与最终分：min(词库100, AI35)=35
        AiReviewRule copyright = new AiReviewRule();
        copyright.setRuleId(2L);
        copyright.setRuleName("版权查重");
        copyright.setRuleType("copyright");
        copyright.setRuleContent("plagiarism_check");
        copyright.setRuleValue("plagiarism_check");
        copyright.setThreshold(90);
        copyright.setAction("auto_reject");
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(copyright));

        Map<String, Object> r = aiReviewService.score(22L, "他拿起刀，用暴力解决了一切");
        assertEquals(200, r.get("code"));
        assertEquals(35, r.get("aiScore"));

        org.mockito.ArgumentCaptor<ReviewRecord> captor = org.mockito.ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordMapper).updateReviewRecord(captor.capture());
        String aiResult = captor.getValue().getAiResult();
        assertTrue(aiResult.contains("\"issues\""));
        assertTrue(aiResult.contains("他拿起刀，用暴力解决了一切"));
        assertTrue(aiResult.contains("暴力描写"));
    }

    @Test
    void decideAction_无命中或全部放行_返回null() {
        assertNull(aiReviewService.decideAction(100, "正常内容", Collections.singletonList(contentRule)));
        contentRule.setAction("auto_pass");
        // auto_pass 命中阈值但视为放行 -> null
        assertNull(aiReviewService.decideAction(60, "他拿起刀，用暴力解决了一切", Collections.singletonList(contentRule)));
        assertNull(aiReviewService.decideAction(50, "内容", null));
    }

    @Test
    void score_AI高分_但auto_reject规则命中_仍驳回() {
        // 高分直通的安全兜底：score>=90 但命中 auto_reject 规则（阈值>90 时 AI 分仍低于阈值）-> 优先驳回
        contentRule.setAction("auto_reject");
        contentRule.setRuleValue("plagiarism_check");
        contentRule.setThreshold(95);
        ReflectionTestUtils.setField(aiReviewService, "aiApiKey", "sk-test");
        ReflectionTestUtils.setField(aiReviewService, "aiBaseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(aiReviewService, "aiModel", "deepseek-chat");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(aiReviewService, "restTemplate", mockRest);
        String aiResp = "{\"choices\":[{\"message\":{\"content\":\"{\\\"score\\\":92,\\\"level\\\":\\\"low\\\","
                + "\\\"keywords\\\":[\\\"疑似抄袭\\\"]}\"}}]}";
        when(mockRest.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResp, HttpStatus.OK));

        ReviewRecord record = new ReviewRecord();
        record.setReviewId(70L);
        record.setStatus("ai_reviewing");
        when(reviewRecordMapper.selectReviewRecordById(70L)).thenReturn(record);
        when(aiReviewRuleMapper.selectActiveAiReviewRules()).thenReturn(Collections.singletonList(contentRule));

        Map<String, Object> r = aiReviewService.score(70L, "正常内容");
        // 92>=90 但 92<95 命中 auto_reject -> 仍驳回
        assertEquals(92, ((Number) r.get("aiScore")).intValue());
        assertEquals("rejected", r.get("nextStatus"));
    }
}
