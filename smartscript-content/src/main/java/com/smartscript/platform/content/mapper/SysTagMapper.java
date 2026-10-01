package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysTag;

/**
 * 标签 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_tag 表。
 *
 * @author xiangsipeng
 */
public interface SysTagMapper
{
    /**
     * 查询标签列表
     *
     * @param query 查询条件（tagName 模糊、tagType/status 精确，按需传入）
     * @return 标签集合
     */
    public List<SysTag> selectTagList(SysTag query);

    /**
     * App 侧标签列表（只读）
     *
     * 固定条件：status = '1'；按 use_count 降序（筛选用，热度高的在前）。
     *
     * @param query 查询条件（tagType 精确，可选）
     * @return 标签集合
     */
    public List<SysTag> selectAppTagList(SysTag query);

    /**
     * 通过标签ID查询标签
     *
     * @param tagId 标签ID
     * @return 标签对象
     */
    public SysTag selectTagById(Long tagId);

    /**
     * 校验标签名称是否唯一
     *
     * @param tagName 标签名称
     * @return 命中的标签对象（无则 null）
     */
    public SysTag checkTagNameUnique(String tagName);

    /**
     * 新增标签
     *
     * @param tag 标签
     * @return 影响行数
     */
    public int insertTag(SysTag tag);

    /**
     * 修改标签（动态 set，仅更新非空字段）
     *
     * @param tag 标签
     * @return 影响行数
     */
    public int updateTag(SysTag tag);

    /**
     * 批量删除标签（物理删除，无文档依据，反推：若依原生 sys_post 同为物理删除）
     *
     * @param tagIds 需要删除的标签ID数组
     * @return 影响行数
     */
    public int deleteTagByIds(Long[] tagIds);
}
