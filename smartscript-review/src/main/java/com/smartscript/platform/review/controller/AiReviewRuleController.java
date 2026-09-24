package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.AiReviewRule;
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
}
