package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.AiReviewRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * AI审核规则Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface AiReviewRuleMapper {

    /**
     * 查询AI审核规则列表
     */
    List<AiReviewRule> selectAiReviewRuleList(AiReviewRule aiReviewRule);

    /**
     * 查询AI审核规则详情
     */
    AiReviewRule selectAiReviewRuleById(@Param("ruleId") Long ruleId);

    /**
     * 新增AI审核规则
     */
    int insertAiReviewRule(AiReviewRule aiReviewRule);

    /**
     * 修改AI审核规则
     */
    int updateAiReviewRule(AiReviewRule aiReviewRule);

    /**
     * 删除AI审核规则
     */
    int deleteAiReviewRuleById(@Param("ruleId") Long ruleId);
}
