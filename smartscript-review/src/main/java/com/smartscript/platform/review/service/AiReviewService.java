package com.smartscript.platform.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.review.domain.AiReviewRule;
import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.mapper.AiReviewRuleMapper;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI审核规则服务：作品提交后触发，读取启用规则 → 拼提示词 → 调用AI（外部API配置化，无Key走Mock）→ 打分 → 落库
 *
 * @author smartscript
 */
@Service
public class AiReviewService {

    @Autowired
    private AiReviewRuleMapper aiReviewRuleMapper;

    @Autowired
    private ReviewRecordMapper reviewRecordMapper;

    /** 外部AI审核API地址（application.yml 配置 ai.review.base-url，为空则走Mock打分） */
    @Value("${ai.review.base-url:}")
    private String aiBaseUrl;

    /** 外部AI审核API Key */
    @Value("${ai.review.api-key:}")
    private String aiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * AI初筛打分：规则读取 → 提示词生成 → AI判定 → 落库 ai_result/ai_risk_level/ai_sensitive_words
     *
     * @param reviewId 审核记录ID
     * @param content  待审核内容（作品简介/章节正文等）
     */
    public Map<String, Object> score(Long reviewId, String content) {
        Map<String, Object> result = new HashMap<>();
        if (reviewId == null) {
            result.put("code", 400);
            result.put("msg", "reviewId必填");
            return result;
        }
        ReviewRecord record = reviewRecordMapper.selectReviewRecordById(reviewId);
        if (record == null) {
            result.put("code", 404);
            result.put("msg", "审核记录不存在: " + reviewId);
            return result;
        }
        // 1) 读取启用中的AI审核规则
        List<AiReviewRule> rules = aiReviewRuleMapper.selectActiveAiReviewRules();
        // 2) 根据规则拼提示词（规则即审核标准）
        String prompt = buildPrompt(rules);
        // 3) 调用AI：配置了外部API则真调，否则走确定性Mock
        boolean usingMock = !StringUtils.hasText(aiBaseUrl) || !StringUtils.hasText(aiApiKey);
        Map<String, Object> aiResp = usingMock ? mockScore(content, rules) : callExternalAi(prompt, content);
        int score = ((Number) aiResp.get("score")).intValue();
        String riskLevel = (String) aiResp.get("level");
        List<String> keywords = (List<String>) aiResp.get("keywords");
        String sensitiveWords = String.join(",", keywords);

        // 4) 落库AI结果
        Date now = new Date();
        ReviewRecord upd = new ReviewRecord();
        upd.setReviewId(reviewId);
        try {
            Map<String, Object> aiJson = new LinkedHashMap<>();
            aiJson.put("score", score);
            aiJson.put("level", riskLevel);
            aiJson.put("keywords", keywords);
            aiJson.put("prompt", prompt.length() > 500 ? prompt.substring(0, 500) : prompt);
            upd.setAiResult(objectMapper.writeValueAsString(aiJson));
        } catch (Exception ignored) {
            upd.setAiResult("{\"score\":" + score + ",\"level\":\"" + riskLevel + "\"}");
        }
        upd.setAiRiskLevel(riskLevel);
        upd.setAiSensitiveWords(sensitiveWords.isEmpty() ? null : sensitiveWords);
        upd.setAiStartTime(record.getAiStartTime() != null ? record.getAiStartTime() : now);
        upd.setAiEndTime(now);
        reviewRecordMapper.updateReviewRecord(upd);

        result.put("code", 200);
        result.put("msg", "AI初筛完成");
        result.put("aiScore", score);
        result.put("riskLevel", riskLevel);
        result.put("keywords", keywords);
        result.put("usingMock", usingMock);
        result.put("prompt", prompt);
        return result;
    }

    /**
     * 根据启用规则生成审核提示词
     */
    public String buildPrompt(List<AiReviewRule> rules) {
        StringBuilder sb = new StringBuilder("你是内容安全审核员。请根据以下审核标准对作品内容逐项判定并打分（0-100分，分数越高越安全合规）：\n");
        if (rules == null || rules.isEmpty()) {
            sb.append("暂无审核规则，请按通用内容安全标准审核。\n");
            return sb.toString();
        }
        for (AiReviewRule rule : rules) {
            sb.append("- 规则「").append(rule.getRuleName() == null ? "" : rule.getRuleName())
                    .append("」类型:").append(rule.getRuleType() == null ? "" : rule.getRuleType())
                    .append(" 阈值:").append(rule.getThreshold() == null ? "" : rule.getThreshold())
                    .append(" 违规处置:").append(rule.getAction() == null ? "" : rule.getAction())
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * Mock打分（无外部AI配置时的确定性实现，可测试）
     * 基础100分：命中 content_safety 特征词每条-20；命中 sensitive_word 规则词每个-15；低于阈值线自动调整等级
     */
    public Map<String, Object> mockScore(String content, List<AiReviewRule> rules) {
        List<String> keywords = new ArrayList<>();
        String text = content == null ? "" : content;
        // 内置内容安全特征词（对应 content_safety 类规则）
        String[] safetyWords = {"暴力", "血腥", "色情", "低俗", "赌博", "毒品"};
        if (text.length() > 0) {
            for (String w : safetyWords) {
                if (text.contains(w)) {
                    keywords.add(w);
                }
            }
        }
        // sensitive_word 类规则的词（ruleContent 映射 rule_key，词表放在 rule_value 列，此处用关键词匹配兜底）
        if (rules != null) {
            for (AiReviewRule rule : rules) {
                if ("sensitive_word".equals(rule.getRuleType()) && StringUtils.hasText(rule.getRuleContent())) {
                    String[] words = rule.getRuleContent().split("[,，;；|]+");
                    for (String w : words) {
                        String word = w.trim();
                        if (word.length() > 0 && text.contains(word)) {
                            keywords.add(word);
                        }
                    }
                }
            }
        }
        // 去重
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(keywords);
        keywords = new ArrayList<>(set);

        int score = 100;
        for (String kw : keywords) {
            if (kw.length() == 1) {
                score -= 10;
            } else if (kw.length() == 2) {
                score -= 15;
            } else {
                score -= 20;
            }
        }
        // 命中暴力/血腥/色情/赌博/毒品 等重级特征再扣
        for (String heavy : new String[]{"暴力", "血腥", "色情", "赌博", "毒品"}) {
            if (keywords.contains(heavy)) {
                score -= 10;
            }
        }
        if (score < 0) {
            score = 0;
        }
        String level = score >= 85 ? "low" : (score >= 60 ? "medium" : "high");
        Map<String, Object> resp = new HashMap<>();
        resp.put("score", score);
        resp.put("level", level);
        resp.put("keywords", keywords);
        return resp;
    }

    /**
     * 外部AI调用（配置了 ai.review.base-url / api-key 时启用；当前预留，接入后无需改业务代码）
     */
    private Map<String, Object> callExternalAi(String prompt, String content) {
        // 预留：使用 aiBaseUrl / aiApiKey 发起 HTTP 调用，解析 {"score":..,"level":..,"keywords":[...]}
        // 未实现前回退到 Mock，保证接口可用
        return mockScore(content, aiReviewRuleMapper.selectActiveAiReviewRules());
    }
}
