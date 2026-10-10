package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.Blacklist;
import com.smartscript.platform.review.domain.RiskRule;
import com.smartscript.platform.review.mapper.BlacklistMapper;
import com.smartscript.platform.review.mapper.RiskRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 风控 check 核心逻辑单元测试
 * 覆盖：黑名单拦截 / 敏感词命中 / 无风险放行 / JSON与纯文本敏感词解析
 */
@ExtendWith(MockitoExtension.class)
class RiskCheckServiceTest {

    @Mock
    private BlacklistMapper blacklistMapper;

    @Mock
    private RiskRuleMapper riskRuleMapper;

    @InjectMocks
    private RiskCheckService riskCheckService;

    private RiskRule sensitiveRule;

    @BeforeEach
    void setUp() {
        sensitiveRule = new RiskRule();
        sensitiveRule.setRuleId(8L);
        sensitiveRule.setRuleName("敏感词测试规则");
        sensitiveRule.setRuleType("sensitive");
        sensitiveRule.setRuleContent("11");
    }

    @Test
    void check_黑名单命中_返回HIGH并拦截() {
        Blacklist black = new Blacklist();
        black.setId(4L);
        black.setTargetType("user");
        black.setTargetValue("111");
        black.setReason("违规用户");
        black.setStatus("enabled");
        when(blacklistMapper.selectActiveByTarget(anyString(), anyString())).thenReturn(black);

        Map<String, Object> r = riskCheckService.check(111L, "随便什么内容");
        assertEquals("HIGH", r.get("riskLevel"));
        assertEquals("BLOCK", r.get("action"));
        assertEquals(Boolean.TRUE, r.get("blocked"));
    }

    @Test
    void check_敏感词命中_返回MEDIUM转审核() {
        when(blacklistMapper.selectActiveByTarget(anyString(), anyString())).thenReturn(null);
        when(riskRuleMapper.selectActiveSensitiveRules()).thenReturn(Collections.singletonList(sensitiveRule));

        Map<String, Object> r = riskCheckService.check(106L, "这是第11章内容");
        assertEquals("MEDIUM", r.get("riskLevel"));
        assertEquals("REVIEW", r.get("action"));
        List<?> hits = (List<?>) r.get("hits");
        assertEquals(1, hits.size());
    }

    @Test
    void check_无敏感词_返回NONE放行() {
        when(blacklistMapper.selectActiveByTarget(anyString(), anyString())).thenReturn(null);
        when(riskRuleMapper.selectActiveSensitiveRules()).thenReturn(Collections.singletonList(sensitiveRule));

        Map<String, Object> r = riskCheckService.check(106L, "这是一篇正常的作品内容");
        assertEquals("NONE", r.get("riskLevel"));
        assertEquals("PASS", r.get("action"));
        assertEquals(Boolean.FALSE, r.get("blocked"));
    }

    @Test
    void check_JSON敏感词数组_可解析() {
        sensitiveRule.setRuleContent("{\"words\":[\"敏感\",\"违禁\"]}");
        when(blacklistMapper.selectActiveByTarget(anyString(), anyString())).thenReturn(null);
        when(riskRuleMapper.selectActiveSensitiveRules()).thenReturn(Collections.singletonList(sensitiveRule));

        Map<String, Object> r = riskCheckService.check(106L, "内容中包含违禁词");
        assertEquals("MEDIUM", r.get("riskLevel"));
        List<?> hits = (List<?>) r.get("hits");
        assertEquals(1, hits.size());
    }

    @Test
    void check_逗号分隔敏感词_可解析() {
        sensitiveRule.setRuleContent("敏感,违禁,禁词");
        when(blacklistMapper.selectActiveByTarget(anyString(), anyString())).thenReturn(null);
        when(riskRuleMapper.selectActiveSensitiveRules()).thenReturn(Collections.singletonList(sensitiveRule));

        Map<String, Object> r = riskCheckService.check(106L, "这是一篇正常的文章内容，没有违规之处");
        assertEquals("NONE", r.get("riskLevel"));
        assertEquals("PASS", r.get("action"));
    }

    @Test
    void check_缺userId_返回400() {
        assertEquals(400, riskCheckService.check(null, "内容").get("code"));
    }
}
