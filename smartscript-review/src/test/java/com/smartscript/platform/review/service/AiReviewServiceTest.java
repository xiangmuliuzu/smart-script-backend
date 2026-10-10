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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    void mockScore_含暴力词_扣分转中风险() {
        Map<String, Object> r = aiReviewService.mockScore("他拿起刀，用暴力解决了一切", Collections.singletonList(contentRule));
        assertTrue(((Number) r.get("score")).intValue() < 100);
        assertEquals("medium", r.get("level"));
        assertTrue(((java.util.List<?>) r.get("keywords")).contains("暴力"));
    }

    @Test
    void mockScore_命中敏感词规则_扣分并记录() {
        Map<String, Object> r = aiReviewService.mockScore("内容里出现了违禁词汇", Collections.singletonList(sensitiveRule));
        assertTrue(((Number) r.get("score")).intValue() < 100);
        assertTrue(((java.util.List<?>) r.get("keywords")).contains("违禁"));
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
}
