package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.AiSkill;
import com.smartscript.platform.content.mapper.AiSkillMapper;
import com.smartscript.platform.content.service.IAiSkillService;

/**
 * AI 导演风格 skill 服务实现
 *
 * 名称唯一校验：新增/修改均校验 skill_name（修改排除自身），重复抛
 * IllegalArgumentException（控制器转 400），绝不落库重复名称。
 *
 * @author xiangsipeng
 */
@Service
public class AiSkillServiceImpl implements IAiSkillService
{
    @Autowired
    private AiSkillMapper aiSkillMapper;

    @Override
    public List<AiSkill> selectSkillList(AiSkill query)
    {
        return aiSkillMapper.selectSkillList(query);
    }

    @Override
    public AiSkill selectSkillById(Long id)
    {
        return aiSkillMapper.selectSkillById(id);
    }

    @Override
    public AiSkill insertSkill(AiSkill skill)
    {
        AiSkill hit = aiSkillMapper.checkSkillNameUnique(skill.getSkillName(), null);
        if (hit != null)
        {
            throw new IllegalArgumentException("skill 名称已存在: " + skill.getSkillName());
        }
        aiSkillMapper.insertSkill(skill);
        return skill;
    }

    @Override
    public AiSkill updateSkill(AiSkill skill)
    {
        AiSkill hit = aiSkillMapper.checkSkillNameUnique(skill.getSkillName(), skill.getId());
        if (hit != null)
        {
            throw new IllegalArgumentException("skill 名称已存在: " + skill.getSkillName());
        }
        aiSkillMapper.updateSkill(skill);
        return skill;
    }

    @Override
    public void deleteSkillById(Long id)
    {
        aiSkillMapper.deleteSkillById(id);
    }

    @Override
    public List<AiSkill> selectEnabledSkillBrief()
    {
        return aiSkillMapper.selectEnabledSkillBrief();
    }

    @Override
    public String resolveSkillContent(Long skillId)
    {
        if (skillId == null)
        {
            return null;
        }
        AiSkill skill = aiSkillMapper.selectSkillById(skillId);
        if (skill == null || !"0".equals(skill.getStatus()))
        {
            return null;
        }
        String content = skill.getFileContent();
        return (content == null || content.isBlank()) ? null : content.trim();
    }
}
