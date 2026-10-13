package com.ruoyi.web.controller.pc;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.smartscript.platform.content.domain.SysCopyrightCenterConfig;
import com.smartscript.platform.content.domain.SysCopyrightApplication;
import com.smartscript.platform.content.service.ISysCopyrightCenterConfigService;
import com.smartscript.platform.content.service.ISysCopyrightApplicationService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 版权中心配置Controller
 * 
 * @author SmartScript Team
 * @date 2024-01-15
 */
@RestController
@RequestMapping("/api/v1/admin/content/copyright/config")
public class CopyrightCenterConfigController extends BaseController
{
    @Autowired
    private ISysCopyrightCenterConfigService copyrightCenterConfigService;

    @Autowired
    private ISysCopyrightApplicationService copyrightApplicationService;

    /**
     * 查询版权中心配置列表
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:config:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysCopyrightCenterConfig copyrightCenterConfig)
    {
        startPage();
        List<SysCopyrightCenterConfig> list = copyrightCenterConfigService.selectCopyrightCenterConfigList(copyrightCenterConfig);
        return getDataTable(list);
    }

    /**
     * 导出版权中心配置列表
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:config:export')")
    @Log(title = "版权中心配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysCopyrightCenterConfig copyrightCenterConfig)
    {
        List<SysCopyrightCenterConfig> list = copyrightCenterConfigService.selectCopyrightCenterConfigList(copyrightCenterConfig);
        ExcelUtil<SysCopyrightCenterConfig> util = new ExcelUtil<SysCopyrightCenterConfig>(SysCopyrightCenterConfig.class);
        util.exportExcel(response, list, "版权中心配置数据");
    }

    /**
     * 获取版权中心配置详细信息
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:config:query')")
    @GetMapping(value = "/{configId}")
    public AjaxResult getInfo(@PathVariable("configId") Long configId)
    {
        return success(copyrightCenterConfigService.selectCopyrightCenterConfigByConfigId(configId));
    }

    /**
     * 新增版权中心配置
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:config:add')")
    @Log(title = "版权中心配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SysCopyrightCenterConfig copyrightCenterConfig)
    {
        return toAjax(copyrightCenterConfigService.insertCopyrightCenterConfig(copyrightCenterConfig));
    }

    /**
     * 修改版权中心配置
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:config:edit')")
    @Log(title = "版权中心配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SysCopyrightCenterConfig copyrightCenterConfig)
    {
        return toAjax(copyrightCenterConfigService.updateCopyrightCenterConfig(copyrightCenterConfig));
    }

    /**
     * 删除版权中心配置
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:config:remove')")
    @Log(title = "版权中心配置", businessType = BusinessType.DELETE)
	@DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable Long[] configIds)
    {
        return toAjax(copyrightCenterConfigService.deleteCopyrightCenterConfigByConfigIds(configIds));
    }

    /**
     * 测试连接
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:config:test')")
    @GetMapping("/testConnection/{configId}")
    public AjaxResult testConnection(@PathVariable Long configId)
    {
        boolean result = copyrightCenterConfigService.testConnection(configId);
        return result ? success("连接成功") : error("连接失败");
    }

    /**
     * 获取所有启用的版权中心列表（用于提交申请时选择）
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:config:query')")
    @GetMapping("/active")
    public AjaxResult getActiveConfigList()
    {
        return success(copyrightCenterConfigService.getActiveCenterList());
    }

    /**
     * 修改版权中心状态
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:config:edit')")
    @Log(title = "修改版权中心状态", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysCopyrightCenterConfig config)
    {
        return toAjax(copyrightCenterConfigService.updateCopyrightCenterConfig(config));
    }

    /**
     * 获取指定版权中心配置下的申请列表（配置页专用）
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:config:query')")
    @GetMapping("/{configId}/applications")
    public TableDataInfo getApplicationsByConfigId(
            @PathVariable("configId") Long configId,
            SysCopyrightApplication copyrightApplication)
    {
        copyrightApplication.setConfigId(configId);
        startPage();
        List<SysCopyrightApplication> list = copyrightApplicationService.selectCopyrightApplicationList(copyrightApplication);
        return getDataTable(list);
    }
}
