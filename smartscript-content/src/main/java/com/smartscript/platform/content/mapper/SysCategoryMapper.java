package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysCategory;

/**
 * 剧本分类 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_category 表。
 *
 * @author xiangsipeng
 */
public interface SysCategoryMapper
{
    /**
     * 查询剧本分类列表
     *
     * @param query 查询条件（categoryName 模糊、categoryType/status 精确，按需传入）
     * @return 剧本分类集合
     */
    public List<SysCategory> selectCategoryList(SysCategory query);

    /**
     * 通过分类ID查询剧本分类
     *
     * @param categoryId 分类ID
     * @return 剧本分类对象
     */
    public SysCategory selectCategoryById(Long categoryId);

    /**
     * 校验分类名称是否唯一
     *
     * @param categoryName 分类名称
     * @return 命中的分类对象（无则 null）
     */
    public SysCategory checkCategoryNameUnique(String categoryName);

    /**
     * 新增剧本分类
     *
     * @param category 剧本分类
     * @return 影响行数
     */
    public int insertCategory(SysCategory category);

    /**
     * 修改剧本分类（动态 set，仅更新非空字段；也用于改状态/改排序）
     *
     * @param category 剧本分类
     * @return 影响行数
     */
    public int updateCategory(SysCategory category);
}
