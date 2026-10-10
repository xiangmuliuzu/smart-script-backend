package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysTag;

/**
 * 标签 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_tag 表 + PC 功能清单（标签管理页：
 * 新增、编辑、删除；清单未列启用/停用/排序，故不提供）。
 *
 * @author xiangsipeng
 */
public interface ISysTagService
{
    /**
     * 查询标签列表
     *
     * @param query 查询条件
     * @return 标签集合
     */
    public List<SysTag> selectTagList(SysTag query);

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
     * @param tag 标签信息（编辑时携带 tagId 排除自身）
     * @return true=唯一 false=不唯一
     */
    public boolean checkTagNameUnique(SysTag tag);

    /**
     * 新增标签
     *
     * @param tag 标签
     * @return 影响行数
     */
    public int insertTag(SysTag tag);

    /**
     * 修改标签
     *
     * @param tag 标签
     * @return 影响行数
     */
    public int updateTag(SysTag tag);

    /**
     * 批量删除标签（物理删除）
     *
     * @param tagIds 需要删除的标签ID数组
     * @return 影响行数
     */
    public int deleteTagByIds(Long[] tagIds);
}
