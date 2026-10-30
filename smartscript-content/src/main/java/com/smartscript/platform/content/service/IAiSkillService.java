package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.AiSkill;

/**
 * AI 导演风格 skill 服务
 *
 * @author xiangsipeng
 */
public interface IAiSkillService
{
    /**
     * 查询 skill 列表（管理员端，不含 file_content）
     *
     * @param query 查询条件（skillName 模糊、status 精确）
     * @return skill 集合
     */
    List<AiSkill> selectSkillList(AiSkill query);

    /**
     * 通过 ID 查询 skill（含 file_content）
     *
     * @param id skill ID
     * @return skill（无则 null）
     */
    AiSkill selectSkillById(Long id);

    /**
     * 新增 skill（校验名称唯一）
     *
     * @param skill skill
     * @return 新增后的 skill（含 id）
     */
    AiSkill insertSkill(AiSkill skill);

    /**
     * 修改 skill（校验名称唯一，排除自身；动态更新非空字段）
     *
     * @param skill skill
     * @return 修改后的 skill
     */
    AiSkill updateSkill(AiSkill skill);

    /**
     * 删除 skill（物理删除）
     *
     * @param id skill ID
     */
    void deleteSkillById(Long id);

    /**
     * 查询全部正常 skill 轻量列表（用户端「导演(skill)选择」下拉用）
     *
     * @return skill 集合（不含 file_content）
     */
    List<AiSkill> selectEnabledSkillBrief();

    /**
     * 读取指定 skill 的文本内容（AI 调用注入 prompt 用）。
     *
     * skill 不存在 / 已停用 / 内容为空时返回 null，调用方按「未选择 skill」处理，
     * 不阻断 AI 调用。
     *
     * @param skillId skill ID
     * @return skill 文本内容（无则 null）
     */
    String resolveSkillContent(Long skillId);
}
