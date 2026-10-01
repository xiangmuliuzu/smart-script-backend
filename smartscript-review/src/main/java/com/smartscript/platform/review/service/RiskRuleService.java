package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.RiskRule;
import com.smartscript.platform.review.mapper.RiskRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 风控规则Service业务层处理
 *
 * @author smartscript
 */
@Service
public class RiskRuleService {

    @Autowired
    private RiskRuleMapper riskRuleMapper;

    /**
     * 查询风控规则列表
     */
    public List<RiskRule> selectRiskRuleList(RiskRule riskRule) {
        return riskRuleMapper.selectRiskRuleList(riskRule);
    }

    /**
     * 查询风控规则详情
     */
    public RiskRule selectRiskRuleById(Long ruleId) {
        return riskRuleMapper.selectRiskRuleById(ruleId);
    }

    /**
     * 新增风控规则
     */
    public int insertRiskRule(RiskRule riskRule) {
        return riskRuleMapper.insertRiskRule(riskRule);
    }

    /**
     * 修改风控规则
     */
    public int updateRiskRule(RiskRule riskRule) {
        return riskRuleMapper.updateRiskRule(riskRule);
    }

    /**
     * 删除风控规则
     */
    public int deleteRiskRuleById(Long ruleId) {
        return riskRuleMapper.deleteRiskRuleById(ruleId);
    }
}
