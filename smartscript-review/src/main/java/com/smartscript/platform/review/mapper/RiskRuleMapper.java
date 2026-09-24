package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.RiskRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 风控规则Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface RiskRuleMapper {

    /**
     * 查询风控规则列表
     */
    List<RiskRule> selectRiskRuleList(RiskRule riskRule);

    /**
     * 查询风控规则详情
     */
    RiskRule selectRiskRuleById(@Param("ruleId") Long ruleId);

    /**
     * 新增风控规则
     */
    int insertRiskRule(RiskRule riskRule);

    /**
     * 修改风控规则
     */
    int updateRiskRule(RiskRule riskRule);

    /**
     * 删除风控规则
     */
    int deleteRiskRuleById(@Param("ruleId") Long ruleId);
}
