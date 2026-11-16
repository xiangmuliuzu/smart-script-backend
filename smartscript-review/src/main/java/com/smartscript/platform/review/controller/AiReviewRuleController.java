package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.AiReviewRule;
import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import com.smartscript.platform.review.service.AiReviewRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI审核规则Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/review/ai-rule")
public class AiReviewRuleController {

    @Autowired
    private AiReviewRuleService aiReviewRuleService;

    @Autowired
    private ReviewRecordMapper reviewRecordMapper;

    /**
     * AI审核准确率统计：AI初筛建议与人工终审结果的一致性
     * AI建议 = ai_score >= 70 判为通过，否则驳回；与人工终审状态对比
     */
    @GetMapping("/statistics")
    public Map<String, Object> statistics() {
        Map<String, Object> result = new HashMap<>();
        List<ReviewRecord> records = reviewRecordMapper.selectReviewRecordList(new ReviewRecord());
        int total = 0, consistent = 0, aiPass = 0, aiReject = 0;
        for (ReviewRecord r : records) {
            Integer score = r.getAiScore();
            String status = r.getStatus();
            if (score == null || status == null) continue;
            if (!"approved".equals(status) && !"rejected".equals(status)) continue;
            total++;
            boolean aiSuggestPass = score >= 70;
            if (aiSuggestPass) aiPass++; else aiReject++;
            boolean humanPass = "approved".equals(status);
            if (aiSuggestPass == humanPass) consistent++;
        }
        double accuracy = total == 0 ? 0 : Math.round(consistent * 10000.0 / total) / 100.0;
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("total", total);
        result.put("consistent", consistent);
        result.put("aiPass", aiPass);
        result.put("aiReject", aiReject);
        result.put("accuracy", accuracy);
        return result;
    }

    /**
     * 查询AI审核规则列表
     */
    @GetMapping("/list")
    public Map<String, Object> list(AiReviewRule aiReviewRule) {
        Map<String, Object> result = new HashMap<>();
        List<AiReviewRule> list = aiReviewRuleService.selectAiReviewRuleList(aiReviewRule);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 查询AI审核规则详情
     */
    @GetMapping("/detail/{ruleId}")
    public Map<String, Object> detail(@PathVariable("ruleId") Long ruleId) {
        Map<String, Object> result = new HashMap<>();
        AiReviewRule aiReviewRule = aiReviewRuleService.selectAiReviewRuleById(ruleId);
        if (aiReviewRule == null) {
            result.put("code", 404);
            result.put("msg", "未找到该规则");
        } else {
            result.put("code", 200);
            result.put("msg", "操作成功");
            result.put("data", aiReviewRule);
        }
        return result;
    }

    /**
     * 新增AI审核规则
     */
    @PostMapping
    public Map<String, Object> add(@RequestBody AiReviewRule aiReviewRule) {
        if (aiReviewRule.getRuleContent() == null || aiReviewRule.getRuleContent().isEmpty()) {
            aiReviewRule.setRuleContent("rule_" + System.currentTimeMillis());
        }
        if (aiReviewRule.getSort() == null) {
            aiReviewRule.setSort(0);
        }
        if (aiReviewRule.getStatus() == null || aiReviewRule.getStatus().isEmpty()) {
            aiReviewRule.setStatus("enabled");
        }
        Map<String, Object> result = new HashMap<>();
        aiReviewRuleService.insertAiReviewRule(aiReviewRule);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 修改AI审核规则
     */
    @PutMapping
    public Map<String, Object> edit(@RequestBody AiReviewRule aiReviewRule) {
        Map<String, Object> result = new HashMap<>();
        aiReviewRuleService.updateAiReviewRule(aiReviewRule);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 删除AI审核规则
     */
    @DeleteMapping("/{ruleId}")
    public Map<String, Object> remove(@PathVariable("ruleId") Long ruleId) {
        Map<String, Object> result = new HashMap<>();
        aiReviewRuleService.deleteAiReviewRuleById(ruleId);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 启用/停用规则
     */
    @PostMapping("/toggle-status/{ruleId}")
    public Map<String, Object> toggleStatus(@PathVariable("ruleId") Long ruleId, @RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        String status = params.get("status").toString();
        AiReviewRule rule = new AiReviewRule();
        rule.setRuleId(ruleId);
        rule.setStatus(status);
        aiReviewRuleService.updateAiReviewRule(rule);
        result.put("code", 200);
        result.put("msg", "状态更新成功");
        return result;
    }
}
