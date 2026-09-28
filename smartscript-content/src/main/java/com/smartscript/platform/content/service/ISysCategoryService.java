package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysCategory;

/**
 * 剧本分类 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_category 表 + PC 功能清单（分类管理页：
 * 新增、编辑、启用、停用、排序；清单未列删除，故不提供）。
 *
 * @author xiangsipeng
 */
public interface ISysCategoryService
{
    /**
     * 查询剧本分类列表
     *
     * @param query 查询条件
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
     * @param category 分类信息（编辑时携带 categoryId 排除自身）
     * @return true=唯一 false=不唯一
     */
    public boolean checkCategoryNameUnique(SysCategory category);

    /**
     * 新增剧本分类
     *
     * @param category 剧本分类
     * @return 影响行数
     */
    public int insertCategory(SysCategory category);

    /**
     * 修改剧本分类
     *
     * @param category 剧本分类
     * @return 影响行数
     */
    public int updateCategory(SysCategory category);

    /**
     * 启用/停用剧本分类（只更新 status 字段）
     *
     * @param category 仅携带 categoryId + status + updateBy
     * @return 影响行数
     */
    public int updateCategoryStatus(SysCategory category);

    /**
     * 调整剧本分类排序（只更新 sort 字段）
     *
     * @param category 仅携带 categoryId + sort + updateBy
     * @return 影响行数
     */
    public int updateCategorySort(SysCategory category);
}
