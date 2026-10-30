package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.AiSkill;

/**
 * AI 导演风格 skill 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_ai_skill 表。
 *
 * @author xiangsipeng
 */
public interface AiSkillMapper
{
    /**
     * 查询 skill 列表（skillName 模糊、status 精确，按需传入）
     *
     * @param query 查询条件
     * @return skill 集合（不含 file_content，列表回显不携带大文本）
     */
    List<AiSkill> selectSkillList(AiSkill query);

    /**
     * 通过 ID 查询 skill（含 file_content，供回显/注入 prompt）
     *
     * @param id skill ID
     * @return skill 对象（无则 null）
     */
    AiSkill selectSkillById(Long id);

    /**
     * 校验 skill 名称唯一（skillName 精确，排除自身 ID）
     *
     * @param skillName 名称
     * @param excludeId 需排除的 ID（可为 null）
     * @return 命中的 skill（无则 null）
     */
    AiSkill checkSkillNameUnique(@Param("skillName") String skillName,
                                  @Param("excludeId") Long excludeId);

    /**
     * 查询全部正常（status=0）skill 的轻量列表（不含 file_content）
     *
     * 供用户端「导演(skill)选择」下拉使用。
     *
     * @return skill 集合
     */
    List<AiSkill> selectEnabledSkillBrief();

    /**
     * 新增 skill
     *
     * @param skill skill
     * @return 影响行数
     */
    int insertSkill(AiSkill skill);

    /**
     * 修改 skill（动态 set，仅更新非空字段；也用于停用/启用）
     *
     * @param skill skill
     * @return 影响行数
     */
    int updateSkill(AiSkill skill);

    /**
     * 删除 skill（物理删除）
     *
     * @param id skill ID
     * @return 影响行数
     */
    int deleteSkillById(Long id);
}
