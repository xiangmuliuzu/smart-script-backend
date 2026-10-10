package com.smartscript.platform.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.review.domain.Blacklist;
import com.smartscript.platform.review.domain.RiskRule;
import com.smartscript.platform.review.mapper.BlacklistMapper;
import com.smartscript.platform.review.mapper.RiskRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 内容风控检查
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
}
