package com.smartscript.platform.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.review.domain.AiReviewRule;
import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.mapper.AiReviewRuleMapper;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * AI审核规则服务：作品提交后触发，读取启用规则 → 拼提示词 → 调用AI（DeepSeek）→ 打分 → 落库
 *
 * @author smartscript
 */
@Service
public class AiReviewService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiReviewService.class);

    @Autowired
    private AiReviewRuleMapper aiReviewRuleMapper;

    @Autowired
    private ReviewRecordMapper reviewRecordMapper;

    /** 复用内容创作同一套 DeepSeek 配置；未配置 api-key 时走 Mock 兜底 */
    @Value("${AI_BASE_URL:https://api.deepseek.com}")
    private String aiBaseUrl;

    @Value("${AI_API_KEY:}")
    private String aiApiKey;

    @Value("${AI_SERVICE_MODEL:deepseek-chat}")
    private String aiModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate restTemplate;

    @jakarta.annotation.PostConstruct
    void initRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(90000);
        this.restTemplate = new RestTemplate(f);
    }

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
        // 2) 违禁词词库确定性打分：命中词库中的词即扣分，不依赖AI自主判断
        String text = content == null ? "" : content;
        List<String> wordHits = collectKeywordHits(rules, text);
        int wordScore = calcWordScore(wordHits);
        // 3) 根据规则拼提示词（规则即审核标准，词库随提示词下发给AI辅助判断）
        String prompt = buildPrompt(rules);
        // 4) 调用AI：配置了 api-key 则真调 DeepSeek（版权查重等无词库规则依赖其语义判断），否则走确定性基线
        boolean usingMock = !StringUtils.hasText(aiApiKey);
        Map<String, Object> aiResp;
        if (usingMock) {
            aiResp = mockScore(content, rules);
        } else {
            try {
                aiResp = callExternalAi(prompt, content);
            } catch (Exception e) {
                log.warn("AI审核调用失败，回退基线: {}", e.getMessage());
                aiResp = mockScore(content, rules);
            }
        }
        int aiScore = ((Number) aiResp.get("score")).intValue();
        // 5) 最终分数：词库命中分与AI分取最严（min），词库未命中时版权等无词库规则由AI分决定
        int score = Math.min(wordScore, aiScore);
        String riskLevel = score >= 85 ? "low" : (score >= 60 ? "medium" : "high");
        // 合并命中词与问题引用：词库命中优先，AI 补充
        List<String> keywords = new ArrayList<>(wordHits);
        List<?> aiKeywords = (List<?>) aiResp.get("keywords");
        if (aiKeywords != null) {
            for (Object k : aiKeywords) {
                if (k != null && !k.toString().isBlank() && !keywords.contains(k.toString())) {
                    keywords.add(k.toString());
                }
            }
        }
        List<Map<String, Object>> issues = new ArrayList<>();
        for (String kw : wordHits) {
            Map<String, Object> issue = new HashMap<>();
            issue.put("reason", "命中违禁词词库");
            issue.put("quote", sentenceContaining(text, kw));
            issues.add(issue);
        }
        List<?> aiIssues = (List<?>) aiResp.get("issues");
        if (aiIssues != null) {
            for (Object o : aiIssues) {
                if (o instanceof Map) {
                    issues.add((Map<String, Object>) o);
                }
            }
        }
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
            aiJson.put("issues", issues);
            aiJson.put("prompt", prompt.length() > 500 ? prompt.substring(0, 500) : prompt);
            upd.setAiResult(objectMapper.writeValueAsString(aiJson));
        } catch (Exception ignored) {
            upd.setAiResult("{\"score\":" + score + ",\"level\":\"" + riskLevel + "\"}");
        }
        upd.setAiRiskLevel(riskLevel);
        upd.setAiSensitiveWords(sensitiveWords.isEmpty() ? null : sensitiveWords);
        upd.setAiStartTime(record.getAiStartTime() != null ? record.getAiStartTime() : now);
        upd.setAiEndTime(now);

        // 5) 自动流转状态：仅当当前仍为 ai_reviewing 时按规则处理方式/AI结果推进
        //    优先按「启用规则」判定：配了违禁词词库的规则按词库命中判定；版权等无词库规则按 AI 综合分 < 阈值判定
        //    取最严拦截动作：auto_reject(驳回) > manual_review(进人工复审)，auto_pass 视为放行不拦截；
        //    无规则拦截时按 AI 风险等级兜底：high -> rejected（自动驳回），否则 -> pending（进人工复审）。
        String nextStatus;
        if ("ai_reviewing".equals(record.getStatus())) {
            String ruleAction = decideAction(score, content, rules);
            if ("auto_reject".equals(ruleAction)) {
                nextStatus = "rejected";
            } else if ("manual_review".equals(ruleAction)) {
                nextStatus = "pending";
            } else if ("high".equals(riskLevel)) {
                nextStatus = "rejected";
            } else {
                nextStatus = "pending";
            }
            upd.setStatus(nextStatus);
            log.info("AI初审自动流转: reviewId={}, {} -> {}, score={}, level={}, ruleAction={}",
                    reviewId, record.getStatus(), nextStatus, score, riskLevel, ruleAction);
        } else {
            nextStatus = record.getStatus();
            log.info("AI初审完成但状态已非ai_reviewing({})，不自动流转 reviewId={}", record.getStatus(), reviewId);
        }
        reviewRecordMapper.updateReviewRecord(upd);

        result.put("code", 200);
        result.put("msg", "AI初筛完成");
        result.put("aiScore", score);
        result.put("riskLevel", riskLevel);
        result.put("keywords", keywords);
        result.put("usingMock", usingMock);
        result.put("nextStatus", nextStatus);
        result.put("prompt", prompt);
        return result;
    }

    /**
     * 按启用规则判定最严「拦截」处理方式。
     * 规则命中判定：sensitive_word 类按内容命中词表（ruleContent 按逗号/顿号/分号/竖线切词）；
     * 其余类型（content_safety 等）按 AI 综合分 score < threshold 即命中。
     * 命中规则的处理方式取最严：auto_reject > manual_review；auto_pass 视为放行（不拦截，返回 null 语义等同未触发）。
     * 无规则命中或全部放行时返回 null。
     */
    public String decideAction(int score, String content, List<AiReviewRule> rules) {
        String strictest = null;
        int rank = 0;
        if (rules != null) {
            String text = content == null ? "" : content;
            for (AiReviewRule rule : rules) {
                if (rule == null) {
                    continue;
                }
                if (!isRuleTriggered(rule, score, text)) {
                    continue;
                }
                // auto_pass 命中视为放行（不拦截），不参与最严比较
                if ("auto_pass".equals(rule.getAction())) {
                    continue;
                }
                int r = actionRank(rule.getAction());
                if (r > rank) {
                    rank = r;
                    strictest = rule.getAction();
                }
            }
        }
        return strictest;
    }

    /**
     * 规则命中判定（按优先级）：
     * 1) 配置了违禁词词库（rule_value）的规则：正文命中词库任一词即触发，不依赖AI自主判断；
     * 2) 无词库的 sensitive_word 规则：按 rule_key 旧词表命中；
     * 3) 其余规则（如版权查重）：按 AI 综合分 score < 阈值 判定。
     */
    private boolean isRuleTriggered(AiReviewRule rule, int score, String text) {
        List<String> words = parseRuleWords(rule);
        if (!words.isEmpty()) {
            for (String w : words) {
                if (w.length() > 0 && text.contains(w)) {
                    return true;
                }
            }
            return false;
        }
        if ("sensitive_word".equals(rule.getRuleType()) && StringUtils.hasText(rule.getRuleContent())) {
            String[] oldWords = rule.getRuleContent().split("[,，;；|]+");
            for (String w : oldWords) {
                String word = w.trim();
                if (word.length() > 0 && text.contains(word)) {
                    return true;
                }
            }
            return false;
        }
        return rule.getThreshold() != null && score < rule.getThreshold();
    }

    private int actionRank(String action) {
        if ("auto_reject".equals(action)) {
            return 3;
        }
        if ("manual_review".equals(action)) {
            return 2;
        }
        if ("auto_pass".equals(action)) {
            return 1;
        }
        return 0;
    }

    /**
     * 根据启用规则生成审核提示词（词库随规则下发，供AI参考；版权查重等无词库规则依赖AI语义判断）
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
                    .append(" 违规处置:").append(rule.getAction() == null ? "" : rule.getAction());
            List<String> words = parseRuleWords(rule);
            if (!words.isEmpty()) {
                sb.append(" 违禁词词库:").append(String.join("、", words));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Mock打分（无外部AI配置时的基线实现，可测试）：
     * 词库命中扣分已由 collectKeywordHits + calcWordScore 在 score() 中确定性计算，
     * 此处仅返回 AI 侧基线（100分/低风险），避免与词库分重复扣分。
     */
    public Map<String, Object> mockScore(String content, List<AiReviewRule> rules) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("score", 100);
        resp.put("level", "low");
        resp.put("keywords", new ArrayList<String>());
        resp.put("issues", new ArrayList<Map<String, Object>>());
        return resp;
    }

    /**
     * 收集所有启用规则在正文中的违禁词命中（去重）。
     * 词库来源：rule_value JSON {"words":[...]} 优先；无词库且为 sensitive_word 时回退 rule_key 旧词表。
     */
    private List<String> collectKeywordHits(List<AiReviewRule> rules, String text) {
        List<String> hits = new ArrayList<>();
        if (rules == null || text == null || text.isEmpty()) {
            return hits;
        }
        for (AiReviewRule rule : rules) {
            if (rule == null) {
                continue;
            }
            for (String word : parseRuleWords(rule)) {
                if (word.length() > 0 && text.contains(word)) {
                    hits.add(word);
                }
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(hits));
    }

    /**
     * 解析规则违禁词词库：
     * 仅当 rule_value 为合法 JSON 且含 {"words":[...]} 数组时视为词库；
     * 非 JSON（如旧数据 "plagiarism_check"）视为无词库，回退旧逻辑；
     * 词库为空且为敏感词规则时回退 rule_key 旧词表。去空去重。
     */
    private List<String> parseRuleWords(AiReviewRule rule) {
        List<String> words = new ArrayList<>();
        if (rule == null) {
            return words;
        }
        if (StringUtils.hasText(rule.getRuleValue())) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(rule.getRuleValue());
                com.fasterxml.jackson.databind.JsonNode arr = node.path("words");
                if (arr.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode w : arr) {
                        if (w.isTextual() && StringUtils.hasText(w.asText())) {
                            words.add(w.asText().trim());
                        }
                    }
                }
                // 合法 JSON 但无 words 数组 → 无词库
            } catch (Exception e) {
                // 非 JSON（旧数据占位值）→ 无词库
            }
        }
        if (words.isEmpty() && "sensitive_word".equals(rule.getRuleType()) && StringUtils.hasText(rule.getRuleContent())) {
            words.addAll(Arrays.asList(rule.getRuleContent().split("[,，;；|]+")));
        }
        List<String> cleaned = new ArrayList<>();
        for (String w : words) {
            String t = w.trim();
            if (t.length() > 0 && !cleaned.contains(t)) {
                cleaned.add(t);
            }
        }
        return cleaned;
    }

    /**
     * 词库命中确定性打分：基础100分，命中1字词-10、2字词-15、3字及以上词-20，下限0分。
     */
    private int calcWordScore(List<String> hits) {
        int score = 100;
        for (String w : hits) {
            if (w.length() == 1) {
                score -= 10;
            } else if (w.length() == 2) {
                score -= 15;
            } else {
                score -= 20;
            }
        }
        return Math.max(score, 0);
    }

    /** 取正文中包含指定词的整句（按句号/叹号/问号/换行切句），找不到时回退该词本身 */
    private String sentenceContaining(String text, String word) {
        if (text == null || text.isEmpty() || word == null || word.isEmpty()) {
            return word == null ? "" : word;
        }
        String[] sentences = text.split("[。！？!?\\n]+");
        for (String s : sentences) {
            if (s.contains(word)) {
                return s.trim();
            }
        }
        return word;
    }

    /**
     * 真调 DeepSeek Chat Completions 做内容安全打分。
     * 要求模型严格输出 JSON：{"score":0-100,"level":"low|medium|high","keywords":["违规词"]}
     * 解析失败抛异常，由调用方回退 Mock。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callExternalAi(String prompt, String content) {
        String system = "你是一名严格的内容安全审核员。请根据用户给出的审核规则，对待审核作品内容逐项判定。"
                + "只输出一个JSON对象，不要任何解释、不要markdown代码块包裹，格式严格为："
                + "{\"score\":0到100的整数,\"level\":\"low或medium或high\",\"keywords\":[\"命中的违规词或问题点\"],"
                + "\"issues\":[{\"reason\":\"问题描述\",\"quote\":\"正文中的原文片段，必须逐字引用待审核内容里的原句\"}]}。"
                + "score越高越安全合规：>=85为low（低风险），60-84为medium（中风险），<60为high（高风险）。"
                + "每条问题都必须在issues中给出reason（问题点）与quote（正文原文片段，用于给审核员/用户定位具体位置，"
                + "若正文确实没有明显字面违规内容则issues为空数组）。"
                + "若内容没有问题，keywords为空数组[]、issues为空数组[]、score=100、level=low。";
        String user = prompt + "\n\n【待审核内容】\n" + (content == null ? "" : content);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", system));
        messages.add(message("user", user));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiModel);
        body.put("messages", messages);
        body.put("max_tokens", 2048);
        body.put("temperature", 0.1);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiApiKey.trim());

        String url = aiBaseUrl.replaceAll("/+$", "") + "/v1/chat/completions";
        ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        String raw = resp.getBody();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("AI审核返回空");
        }

        // 解析 choices[0].message.content
        com.fasterxml.jackson.databind.JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("AI返回非JSON: " + e.getMessage());
        }
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        if (text.isBlank()) {
            throw new IllegalStateException("AI返回content为空");
        }

        // 剥离可能的 markdown code fence
        String json = text.trim();
        if (json.startsWith("```")) {
            int firstNl = json.indexOf('\n');
            if (firstNl > 0) json = json.substring(firstNl + 1);
            if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
            json = json.trim();
        }
        // 截取第一个 { 到最后一个 }，防止模型前后加了多余文字
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }

        Map<String, Object> result;
        try {
            result = objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("AI返回JSON解析失败: " + e.getMessage());
        }
        int score = ((Number) result.getOrDefault("score", 50)).intValue();
        String level = String.valueOf(result.getOrDefault("level", "medium"));
        Object kwRaw = result.get("keywords");
        List<String> keywords = new ArrayList<>();
        if (kwRaw instanceof List) {
            for (Object o : (List<?>) kwRaw) {
                if (o != null && !o.toString().isBlank()) keywords.add(o.toString());
            }
        }
        // 解析问题引用（issues[].reason/quote，quote 非空才保留）
        List<Map<String, Object>> issues = new ArrayList<>();
        Object issRaw = result.get("issues");
        if (issRaw instanceof List) {
            for (Object o : (List<?>) issRaw) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<?, ?> m = (Map<?, ?>) o;
                String reason = m.get("reason") == null ? "" : m.get("reason").toString().trim();
                String quote = m.get("quote") == null ? "" : m.get("quote").toString().trim();
                if (quote.isEmpty()) {
                    continue;
                }
                Map<String, Object> issue = new HashMap<>();
                issue.put("reason", reason);
                issue.put("quote", quote);
                issues.add(issue);
            }
        }
        if (score < 0) score = 0;
        if (score > 100) score = 100;
        // 兜底校验 level
        if (!List.of("low", "medium", "high").contains(level)) {
            level = score >= 85 ? "low" : (score >= 60 ? "medium" : "high");
        }
        Map<String, Object> out = new HashMap<>();
        out.put("score", score);
        out.put("level", level);
        out.put("keywords", keywords);
        out.put("issues", issues);
        log.info("AI初审完成: score={}, level={}, keywords={}, issues={}", score, level, keywords, issues.size());
        return out;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
