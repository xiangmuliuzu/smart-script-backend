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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 风控 check 核心逻辑单元测试
 * 覆盖：黑名单拦截 / 敏感词命中 / 无风险放行 / JSON与纯文本敏感词解析 / 频控规则
 */
@ExtendWith(MockitoExtension.class)
class RiskCheckServiceTest {

    @Mock
    private BlacklistMapper blacklistMapper;

    @Mock
    private RiskRuleMapper riskRuleMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

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
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
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

    private RiskRule freqRule(String type, String name, String content, String action) {
        RiskRule rule = new RiskRule();
        rule.setRuleId(9L);
        rule.setRuleName(name);
        rule.setRuleType(type);
        rule.setRuleContent(content);
        rule.setAction(action);
        return rule;
    }

    @Test
    void checkFrequency_超阈值_返回HIGH拦截() {
        when(blacklistMapper.selectActiveByTarget(anyString(), anyString())).thenReturn(null);
        when(riskRuleMapper.selectActiveRulesByType("operation"))
                .thenReturn(Collections.singletonList(freqRule("operation", "批量操作检测", "{\"times\":2,\"window\":\"1m\"}", "ban")));
        when(valueOperations.increment(anyString())).thenReturn(3L);

        Map<String, Object> r = riskCheckService.checkFrequency(106L, "1.2.3.4", "operation");
        assertEquals("HIGH", r.get("riskLevel"));
        assertEquals(Boolean.TRUE, r.get("blocked"));
        List<?> hits = (List<?>) r.get("hits");
        assertEquals(1, hits.size());
        assertEquals("批量操作检测", ((Map<?, ?>) hits.get(0)).get("ruleName"));
    }

    @Test
    void checkFrequency_未超阈值_放行() {
        when(blacklistMapper.selectActiveByTarget(anyString(), anyString())).thenReturn(null);
        when(riskRuleMapper.selectActiveRulesByType("operation"))
                .thenReturn(Collections.singletonList(freqRule("operation", "批量操作检测", "{\"times\":5,\"window\":\"1m\"}", "ban")));
        when(valueOperations.increment(anyString())).thenReturn(2L);

        Map<String, Object> r = riskCheckService.checkFrequency(106L, "1.2.3.4", "operation");
        assertEquals("NONE", r.get("riskLevel"));
        assertEquals(Boolean.FALSE, r.get("blocked"));
    }

    @Test
    void checkFrequency_IP黑名单命中_拦截() {
        Blacklist black = new Blacklist();
        black.setTargetType("ip");
        black.setTargetValue("1.2.3.4");
        black.setReason("恶意IP");
        black.setStatus("enabled");
        when(blacklistMapper.selectActiveByTarget(anyString(), anyString())).thenReturn(null);
        when(blacklistMapper.selectActiveByTarget("ip", "1.2.3.4")).thenReturn(black);

        Map<String, Object> r = riskCheckService.checkFrequency(106L, "1.2.3.4", "operation");
        assertEquals("HIGH", r.get("riskLevel"));
        assertEquals(Boolean.TRUE, r.get("blocked"));
    }

    @Test
    void checkFrequency_Redis异常_降级放行() {
        when(blacklistMapper.selectActiveByTarget(anyString(), anyString())).thenReturn(null);
        when(riskRuleMapper.selectActiveRulesByType("login"))
                .thenReturn(Collections.singletonList(freqRule("login", "频繁登录检测", "{\"times\":5,\"window\":\"1h\"}", "captcha")));
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        Map<String, Object> r = riskCheckService.checkFrequency(null, "1.2.3.4", "login");
        assertEquals("NONE", r.get("riskLevel"));
        assertEquals(Boolean.FALSE, r.get("blocked"));
    }

    @Test
    void checkFrequency_无userId_按IP计数() {
        when(blacklistMapper.selectActiveByTarget(anyString(), anyString())).thenReturn(null);
        when(riskRuleMapper.selectActiveRulesByType("login"))
                .thenReturn(Collections.singletonList(freqRule("login", "频繁登录检测", "{\"times\":5,\"window\":\"1h\"}", "captcha")));
        when(valueOperations.increment(anyString())).thenReturn(1L);

        Map<String, Object> r = riskCheckService.checkFrequency(null, "1.2.3.4", "login");
        assertEquals("NONE", r.get("riskLevel"));
        // 校验计数键使用了 IP
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(valueOperations).increment(captor.capture());
        assertTrue(captor.getValue().contains("1.2.3.4"));
    }
}
