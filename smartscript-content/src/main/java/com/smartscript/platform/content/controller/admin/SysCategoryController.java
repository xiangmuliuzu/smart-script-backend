package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.smartscript.platform.content.domain.SysCategory;
import com.smartscript.platform.content.service.ISysCategoryService;

/**
 * 剧本分类管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_category 表 + PC 功能清单（分类管理页：
 * 新增、编辑、启用、停用、排序；清单未列删除，故不提供删除接口）。
 * 路由前缀 /api/v1/admin/content/category（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：03 PC 接口文档未列 B 模块分类接口，按云端表结构 + PC 功能清单反推。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/category")
public class SysCategoryController extends BaseController
{
    @Autowired
    private ISysCategoryService categoryService;

    /**
     * 获取剧本分类列表
     * 入参（query，均可选）：categoryName 模糊、categoryType 精确、status 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:category:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysCategory query)
    {
        startPage();
        List<SysCategory> list = categoryService.selectCategoryList(query);
        return getDataTable(list);
    }

    /**
     * 通过分类ID获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('content:category:query')")
    @GetMapping("/{categoryId}")
    public AjaxResult getInfo(@PathVariable Long categoryId)
    {
        return success(categoryService.selectCategoryById(categoryId));
    }

    /**
     * 新增剧本分类
     */
    @PreAuthorize("@ss.hasPermi('content:category:add')")
    @Log(title = "剧本分类", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysCategory category)
    {
        if (!categoryService.checkCategoryNameUnique(category))
        {
            return error("新增分类'" + category.getCategoryName() + "'失败，分类名称已存在");
        }
        category.setCreateBy(getUsername());
        return toAjax(categoryService.insertCategory(category));
    }

    /**
     * 修改剧本分类
     */
    @PreAuthorize("@ss.hasPermi('content:category:edit')")
    @Log(title = "剧本分类", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysCategory category)
    {
        if (!categoryService.checkCategoryNameUnique(category))
        {
            return error("修改分类'" + category.getCategoryName() + "'失败，分类名称已存在");
        }
        category.setUpdateBy(getUsername());
        return toAjax(categoryService.updateCategory(category));
    }

    /**
     * 启用/停用剧本分类（单一职责：只改 status）
     */
    @PreAuthorize("@ss.hasPermi('content:category:edit')")
    @Log(title = "剧本分类", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysCategory category)
    {
        category.setUpdateBy(getUsername());
        return toAjax(categoryService.updateCategoryStatus(category));
    }

    /**
     * 调整剧本分类排序（单一职责：只改 sort）
     */
    @PreAuthorize("@ss.hasPermi('content:category:edit')")
    @Log(title = "剧本分类", businessType = BusinessType.UPDATE)
    @PutMapping("/changeSort")
    public AjaxResult changeSort(@RequestBody SysCategory category)
    {
        category.setUpdateBy(getUsername());
        return toAjax(categoryService.updateCategorySort(category));
    }
}
