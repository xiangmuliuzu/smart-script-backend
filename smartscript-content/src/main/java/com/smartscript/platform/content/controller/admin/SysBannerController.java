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
import com.smartscript.platform.content.domain.SysBanner;
import com.smartscript.platform.content.service.ISysBannerService;

/**
 * Banner管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_banner 表 + PC 功能清单（Banner 管理页：
 * 新增、编辑、启用/停用、排序；清单未列删除，故不提供）。
 * 路由前缀 /api/v1/admin/content/banner（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：03 PC 接口文档未列 B 模块 Banner 接口，按云端表结构 + PC 功能清单反推；
 * changeStatus/changeSort 为局部更新，不做 @Validated 全量校验。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/banner")
public class SysBannerController extends BaseController
{
    @Autowired
    private ISysBannerService bannerService;

    /**
     * 获取Banner列表
     * 入参（query，均可选）：title 模糊、position/status 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:banner:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysBanner query)
    {
        startPage();
        List<SysBanner> list = bannerService.selectBannerList(query);
        return getDataTable(list);
    }

    /**
     * 通过BannerID获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('content:banner:query')")
    @GetMapping("/{bannerId}")
    public AjaxResult getInfo(@PathVariable Long bannerId)
    {
        return success(bannerService.selectBannerById(bannerId));
    }

    /**
     * 新增Banner
     */
    @PreAuthorize("@ss.hasPermi('content:banner:add')")
    @Log(title = "Banner管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysBanner banner)
    {
        banner.setCreateBy(getUsername());
        return toAjax(bannerService.insertBanner(banner));
    }

    /**
     * 修改Banner（动态 set，仅更新非空字段）
     */
    @PreAuthorize("@ss.hasPermi('content:banner:edit')")
    @Log(title = "Banner管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysBanner banner)
    {
        banner.setUpdateBy(getUsername());
        return toAjax(bannerService.updateBanner(banner));
    }

    /**
     * 修改Banner状态（单一职责：只改 status）
     */
    @PreAuthorize("@ss.hasPermi('content:banner:edit')")
    @Log(title = "Banner管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysBanner banner)
    {
        banner.setUpdateBy(getUsername());
        return toAjax(bannerService.updateBannerStatus(banner));
    }

    /**
     * 调整Banner排序（单一职责：只改 sort_order）
     */
    @PreAuthorize("@ss.hasPermi('content:banner:edit')")
    @Log(title = "Banner管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeSort")
    public AjaxResult changeSort(@RequestBody SysBanner banner)
    {
        banner.setUpdateBy(getUsername());
        return toAjax(bannerService.updateBannerSort(banner));
    }
}
