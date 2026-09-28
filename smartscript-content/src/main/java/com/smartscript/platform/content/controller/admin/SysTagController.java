package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.service.ISysTagService;

/**
 * 标签管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_tag 表 + PC 功能清单（标签管理页：
 * 新增、编辑、删除；清单未列启用/停用/排序，故不提供对应接口）。
 * 路由前缀 /api/v1/admin/content/tag（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：03 PC 接口文档未列 B 模块标签接口，按云端表结构 + PC 功能清单反推；
 * 标签删除为物理删除（若依原生 sys_post 同为物理删除）。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/tag")
public class SysTagController extends BaseController
{
    @Autowired
    private ISysTagService tagService;

    /**
     * 获取标签列表
     * 入参（query，均可选）：tagName 模糊、tagType 精确、status 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:tag:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysTag query)
    {
        startPage();
        List<SysTag> list = tagService.selectTagList(query);
        return getDataTable(list);
    }

    /**
     * 通过标签ID获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('content:tag:query')")
    @GetMapping("/{tagId}")
    public AjaxResult getInfo(@PathVariable Long tagId)
    {
        return success(tagService.selectTagById(tagId));
    }

    /**
     * 新增标签
     */
    @PreAuthorize("@ss.hasPermi('content:tag:add')")
    @Log(title = "标签管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysTag tag)
    {
        if (!tagService.checkTagNameUnique(tag))
        {
            return error("新增标签'" + tag.getTagName() + "'失败，标签名称已存在");
        }
        tag.setCreateBy(getUsername());
        return toAjax(tagService.insertTag(tag));
    }

    /**
     * 修改标签
     */
    @PreAuthorize("@ss.hasPermi('content:tag:edit')")
    @Log(title = "标签管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysTag tag)
    {
        if (!tagService.checkTagNameUnique(tag))
        {
            return error("修改标签'" + tag.getTagName() + "'失败，标签名称已存在");
        }
        tag.setUpdateBy(getUsername());
        return toAjax(tagService.updateTag(tag));
    }

    /**
     * 删除标签（支持批量，物理删除）
     */
    @PreAuthorize("@ss.hasPermi('content:tag:remove')")
    @Log(title = "标签管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tagIds}")
    public AjaxResult remove(@PathVariable Long[] tagIds)
    {
        return toAjax(tagService.deleteTagByIds(tagIds));
    }
}
