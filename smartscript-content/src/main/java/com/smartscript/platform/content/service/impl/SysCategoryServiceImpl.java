package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysCategory;
import com.smartscript.platform.content.mapper.SysCategoryMapper;
import com.smartscript.platform.content.service.ISysCategoryService;

/**
 * 剧本分类 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_category 表。
 * 反推处理点：parent_id/sort/status 云端列 NOT NULL 无默认值，新增时此处兜底 0/0/"0"。
 *
 * @author xiangsipeng
 */
@Service
public class SysCategoryServiceImpl implements ISysCategoryService
{
    /** 状态：正常 */
    private static final String STATUS_NORMAL = "0";

    @Autowired
    private SysCategoryMapper categoryMapper;

    @Override
    public List<SysCategory> selectCategoryList(SysCategory query)
    {
        return categoryMapper.selectCategoryList(query);
    }

    @Override
    public SysCategory selectCategoryById(Long categoryId)
    {
        return categoryMapper.selectCategoryById(categoryId);
    }

    @Override
    public boolean checkCategoryNameUnique(SysCategory category)
    {
        Long categoryId = StringUtils.isNull(category.getCategoryId()) ? -1L : category.getCategoryId();
        SysCategory info = categoryMapper.checkCategoryNameUnique(category.getCategoryName());
        if (StringUtils.isNotNull(info) && info.getCategoryId().longValue() != categoryId.longValue())
        {
            return false;
        }
        return true;
    }

    @Override
    public int insertCategory(SysCategory category)
    {
        // 云端列 NOT NULL 无默认值，兜底（无文档依据，反推）
        if (StringUtils.isNull(category.getParentId()))
        {
            category.setParentId(0L);
        }
        if (StringUtils.isNull(category.getSort()))
        {
            category.setSort(0);
        }
        if (StringUtils.isNull(category.getStatus()) || StringUtils.isEmpty(category.getStatus()))
        {
            category.setStatus(STATUS_NORMAL);
        }
        return categoryMapper.insertCategory(category);
    }

    @Override
    public int updateCategory(SysCategory category)
    {
        return categoryMapper.updateCategory(category);
    }

    @Override
    public int updateCategoryStatus(SysCategory category)
    {
        // 防御性裁剪：只放行 status，避免调用方误传其他字段被动态 SQL 一并更新
        SysCategory update = new SysCategory();
        update.setCategoryId(category.getCategoryId());
        update.setStatus(category.getStatus());
        update.setUpdateBy(category.getUpdateBy());
        return categoryMapper.updateCategory(update);
    }

    @Override
    public int updateCategorySort(SysCategory category)
    {
        // 防御性裁剪：只放行 sort
        SysCategory update = new SysCategory();
        update.setCategoryId(category.getCategoryId());
        update.setSort(category.getSort());
        update.setUpdateBy(category.getUpdateBy());
        return categoryMapper.updateCategory(update);
    }
}
