package com.smartscript.platform.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.smartscript.platform.review.domain.Blacklist;
import com.smartscript.platform.review.domain.RiskRule;
import com.smartscript.platform.review.mapper.BlacklistMapper;
import com.smartscript.platform.review.mapper.RiskRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 风控校验业务（App 端提交作品/内容前调用 check 接口触发）
 *
 * @author smartscript
 */
@Service
public class RiskCheckService {

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Autowired
    private RiskRuleMapper riskRuleMapper;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 内容风控检查（作品名/简介/章节正文等）
     *
     * @param userId  操作用户ID
     * @param content 待检查内容（作品名/简介/章节正文等）
     * @return {code, riskLevel: NONE|MEDIUM|HIGH, action: PASS|REVIEW|BLOCK, hits: [{ruleId, ruleName, word}], blocked}
     */
    public Map<String, Object> check(Long userId, String content) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> hits = new ArrayList<>();
        if (userId == null) {
            result.put("code", 400);
            result.put("msg", "userId必填");
            return result;
        }
        // 1) 黑名单校验：命中用户黑名单 → 高风险直接拦截
        Blacklist black = blacklistMapper.selectActiveByTarget("user", String.valueOf(userId));
        if (black != null) {
            result.put("code", 200);
            result.put("riskLevel", "HIGH");
            result.put("action", "BLOCK");
            result.put("blocked", true);
            result.put("msg", "用户已被列入黑名单: " + black.getReason());
            result.put("hits", hits);
            return result;
        }
        // 2) 敏感词校验：命中启用中的 sensitive 规则 → 中风险转人工审核
        if (StringUtils.hasText(content)) {
            List<RiskRule> rules = riskRuleMapper.selectActiveSensitiveRules();
            for (RiskRule rule : rules) {
                List<String> words = parseWords(rule.getRuleContent());
                for (String word : words) {
                    if (word.length() > 0 && content.contains(word)) {
                        Map<String, Object> hit = new HashMap<>();
                        hit.put("ruleId", rule.getRuleId());
                        hit.put("ruleName", rule.getRuleName());
                        hit.put("word", word);
                        hits.add(hit);
                    }
                }
            }
        }
        if (!hits.isEmpty()) {
            result.put("code", 200);
            result.put("riskLevel", "MEDIUM");
            result.put("action", "REVIEW");
            result.put("blocked", false);
            result.put("msg", "命中" + hits.size() + "条敏感词规则");
            result.put("hits", hits);
            return result;
        }
        result.put("code", 200);
        result.put("riskLevel", "NONE");
        result.put("action", "PASS");
        result.put("blocked", false);
        result.put("msg", "检查通过");
        result.put("hits", hits);
        return result;
    }

    /**
     * 行为频控检查（登录、领奖、兑换、AI 调用等操作前调用）
     *
     * @param userId     操作用户ID（登录等未认证场景可传 null，改用 IP 计数）
     * @param ip         客户端IP（黑名单 ip 型校验 + 无 userId 时的计数键）
     * @param actionType 行为类型：login / operation / ip 等（对应 sys_risk_rule.rule_type）
     * @return {code, riskLevel, action, blocked, hits:[{ruleId, ruleName, action, times, window}]}
     */
    public Map<String, Object> checkFrequency(Long userId, String ip, String actionType) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> hits = new ArrayList<>();
        try {
            // 1) 黑名单校验：用户 + IP
            if (userId != null) {
                Blacklist blackUser = blacklistMapper.selectActiveByTarget("user", String.valueOf(userId));
                if (blackUser != null) {
                    return blocked(result, hits, "用户已被列入黑名单: " + blackUser.getReason());
                }
            }
            if (StringUtils.hasText(ip)) {
                Blacklist blackIp = blacklistMapper.selectActiveByTarget("ip", ip);
                if (blackIp != null) {
                    return blocked(result, hits, "IP已被列入黑名单: " + blackIp.getReason());
                }
            }
            // 2) 频控规则：按 actionType 匹配启用中的规则（login/operation/ip）
            if (stringRedisTemplate != null) {
                List<RiskRule> rules = riskRuleMapper.selectActiveRulesByType(actionType);
                for (RiskRule rule : rules) {
                    FreqCfg cfg = parseFreq(rule.getRuleContent());
                    if (cfg == null || cfg.times <= 0 || cfg.windowSec <= 0) {
                        continue;
                    }
                    // 计数键：ip 型规则按 IP；其余优先 userId，无 userId 时按 IP
                    String scopeKey;
                    if ("ip".equals(rule.getRuleType())) {
                        scopeKey = ip;
                    } else {
                        scopeKey = userId != null ? String.valueOf(userId) : ip;
                    }
                    if (!StringUtils.hasText(scopeKey)) {
                        continue;
                    }
                    String key = "risk:cnt:" + rule.getRuleType() + ":" + scopeKey;
                    Long count = stringRedisTemplate.opsForValue().increment(key);
                    if (count != null && count == 1L) {
                        stringRedisTemplate.expire(key, cfg.windowSec, TimeUnit.SECONDS);
                    }
                    if (count != null && count > cfg.times) {
                        Map<String, Object> hit = new HashMap<>();
                        hit.put("ruleId", rule.getRuleId());
                        hit.put("ruleName", rule.getRuleName());
                        hit.put("action", rule.getAction());
                        hit.put("times", cfg.times);
                        hit.put("window", cfg.window);
                        hits.add(hit);
                    }
                }
            }
        } catch (Exception e) {
            // Redis 不可用或解析异常：降级放行，不影响主业务
            result.put("code", 200);
            result.put("riskLevel", "NONE");
            result.put("action", "PASS");
            result.put("blocked", false);
            result.put("msg", "检查通过");
            result.put("hits", hits);
            return result;
        }
        if (!hits.isEmpty()) {
            result.put("code", 200);
            result.put("riskLevel", "HIGH");
            result.put("action", "BLOCK");
            result.put("blocked", true);
            result.put("msg", "触发" + hits.size() + "条频控规则");
            result.put("hits", hits);
            return result;
        }
        result.put("code", 200);
        result.put("riskLevel", "NONE");
        result.put("action", "PASS");
        result.put("blocked", false);
        result.put("msg", "检查通过");
        result.put("hits", hits);
        return result;
    }

    private Map<String, Object> blocked(Map<String, Object> result, List<Map<String, Object>> hits, String msg) {
        result.put("code", 200);
        result.put("riskLevel", "HIGH");
        result.put("action", "BLOCK");
        result.put("blocked", true);
        result.put("msg", msg);
        result.put("hits", hits);
        return result;
    }

    /**
     * 解析频控规则内容 {"times":5,"window":"1h"}；window 支持 s/m/h/d
     */
    private FreqCfg parseFreq(String ruleContent) {
        if (!StringUtils.hasText(ruleContent)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(ruleContent.trim());
            if (node == null || !node.has("times") || !node.has("window")) {
                return null;
            }
            FreqCfg cfg = new FreqCfg();
            cfg.times = node.get("times").asInt(0);
            cfg.window = node.get("window").asText("1h");
            cfg.windowSec = parseWindowSec(cfg.window);
            return cfg;
        } catch (Exception e) {
            return null;
        }
    }

    private long parseWindowSec(String window) {
        if (!StringUtils.hasText(window)) {
            return 3600L;
        }
        String w = window.trim().toLowerCase();
        try {
            long num = Long.parseLong(w.replaceAll("[^0-9]", ""));
            if (w.endsWith("s")) {
                return num;
            }
            if (w.endsWith("m")) {
                return num * 60L;
            }
            if (w.endsWith("d")) {
                return num * 86400L;
            }
            return num * 3600L; // 默认小时
        } catch (NumberFormatException e) {
            return 3600L;
        }
    }

    private static class FreqCfg {
        int times;
        String window;
        long windowSec;
    }

    /**
     * 解析敏感词规则内容：优先 JSON（{"words":[...]}），否则按逗号/换行/空格分隔
     */
    private List<String> parseWords(String ruleContent) {
        List<String> words = new ArrayList<>();
        if (!StringUtils.hasText(ruleContent)) {
            return words;
        }
        String text = ruleContent.trim();
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.isArray()) {
                node.forEach(n -> addWord(words, n.asText()));
                return words;
            }
            if (node.has("words") && node.get("words").isArray()) {
                node.get("words").forEach(n -> addWord(words, n.asText()));
                return words;
            }
        } catch (Exception ignored) {
            // 非 JSON，按文本分隔
        }
        String[] parts = text.split("[,，\n\r;；|]+");
        for (String part : parts) {
            addWord(words, part);
        }
        return words;
    }

    private void addWord(List<String> words, String word) {
        String w = word == null ? "" : word.trim();
        if (w.length() > 0 && !words.contains(w)) {
            words.add(w);
        }
    }

    /**
     * 内容风控：命中敏感词规则时抛出业务异常（供作品提交/章节/AI创作/举报等业务入口调用）
     */
    public void checkContentOrThrow(Long userId, String content) {
        Map<String, Object> r = check(userId, content);
        if (Boolean.TRUE.equals(r.get("blocked"))) {
            throw new ServiceException(String.valueOf(r.get("msg")));
        }
        List<?> hits = (List<?>) r.get("hits");
        if (hits != null && !hits.isEmpty()) {
            StringBuilder sb = new StringBuilder("内容包含敏感词：");
            for (Object h : hits) {
                Map<?, ?> hit = (Map<?, ?>) h;
                sb.append("[").append(hit.get("ruleName")).append("]");
            }
            throw new ServiceException(sb.toString());
        }
    }

    /**
     * 行为频控：触发频控规则时抛出业务异常（供登录/领奖/兑换/AI调用等业务入口调用）
     */
    public void checkFrequencyOrThrow(Long userId, String ip, String actionType) {
        Map<String, Object> r = checkFrequency(userId, ip, actionType);
        if (Boolean.TRUE.equals(r.get("blocked"))) {
            throw new ServiceException("操作过于频繁，请稍后再试");
        }
    }
}
