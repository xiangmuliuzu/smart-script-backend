package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.AiReviewRule;
import com.smartscript.platform.review.mapper.AiReviewRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * AI审核规则Service业务层处理
 *
 * @author smartscript
 */
@Service
public class AiReviewRuleService {

    @Autowired
    private AiReviewRuleMapper aiReviewRuleMapper;

    /**
     * 查询AI审核规则列表
     */
    public List<AiReviewRule> selectAiReviewRuleList(AiReviewRule aiReviewRule) {
        return aiReviewRuleMapper.selectAiReviewRuleList(aiReviewRule);
    }

    /**
     * 查询AI审核规则详情
     */
    public AiReviewRule selectAiReviewRuleById(Long ruleId) {
        return aiReviewRuleMapper.selectAiReviewRuleById(ruleId);
    }

    /**
     * 新增AI审核规则
     */
    public int insertAiReviewRule(AiReviewRule aiReviewRule) {
        return aiReviewRuleMapper.insertAiReviewRule(aiReviewRule);
    }

    /**
     * 修改AI审核规则
     */
    public int updateAiReviewRule(AiReviewRule aiReviewRule) {
        return aiReviewRuleMapper.updateAiReviewRule(aiReviewRule);
    }

    /**
     * 删除AI审核规则
     */
    public int deleteAiReviewRuleById(Long ruleId) {
        return aiReviewRuleMapper.deleteAiReviewRuleById(ruleId);
    }
}
