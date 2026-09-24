package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.RiskRule;
import com.smartscript.platform.review.service.RiskRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 风控规则Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/review/risk-rule")
public class RiskRuleController {

    @Autowired
    private RiskRuleService riskRuleService;

    /**
     * 查询风控规则列表
     */
    @GetMapping("/list")
    public Map<String, Object> list(RiskRule riskRule) {
        Map<String, Object> result = new HashMap<>();
        List<RiskRule> list = riskRuleService.selectRiskRuleList(riskRule);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 查询风控规则详情
     */
    @GetMapping("/detail/{ruleId}")
    public Map<String, Object> detail(@PathVariable("ruleId") Long ruleId) {
        Map<String, Object> result = new HashMap<>();
        RiskRule riskRule = riskRuleService.selectRiskRuleById(ruleId);
        if (riskRule == null) {
            result.put("code", 404);
            result.put("msg", "未找到该规则");
        } else {
            result.put("code", 200);
            result.put("msg", "操作成功");
            result.put("data", riskRule);
        }
        return result;
    }

    /**
     * 新增风控规则
     */
    @PostMapping
    public Map<String, Object> add(@RequestBody RiskRule riskRule) {
        Map<String, Object> result = new HashMap<>();
        riskRuleService.insertRiskRule(riskRule);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 修改风控规则
     */
    @PutMapping
    public Map<String, Object> edit(@RequestBody RiskRule riskRule) {
        Map<String, Object> result = new HashMap<>();
        riskRuleService.updateRiskRule(riskRule);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 删除风控规则
     */
    @DeleteMapping("/{ruleId}")
    public Map<String, Object> remove(@PathVariable("ruleId") Long ruleId) {
        Map<String, Object> result = new HashMap<>();
        riskRuleService.deleteRiskRuleById(ruleId);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }
}
