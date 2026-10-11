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
import com.smartscript.platform.content.domain.SysCopyrightApplication;
import com.smartscript.platform.content.service.ISysCopyrightApplicationService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 版权申请Controller
 * 
 * @author SmartScript Team
 * @date 2024-01-15
 */
@RestController
@RequestMapping("/content/copyright/application")
public class CopyrightApplicationController extends BaseController
{
    @Autowired
    private ISysCopyrightApplicationService copyrightApplicationService;

    /**
     * 查询版权申请列表
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:application:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysCopyrightApplication copyrightApplication)
    {
        startPage();
        List<SysCopyrightApplication> list = copyrightApplicationService.selectCopyrightApplicationList(copyrightApplication);
        return getDataTable(list);
    }

    /**
     * 导出版权申请列表
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:application:export')")
    @Log(title = "版权申请", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysCopyrightApplication copyrightApplication)
    {
        List<SysCopyrightApplication> list = copyrightApplicationService.selectCopyrightApplicationList(copyrightApplication);
        ExcelUtil<SysCopyrightApplication> util = new ExcelUtil<SysCopyrightApplication>(SysCopyrightApplication.class);
        util.exportExcel(response, list, "版权申请数据");
    }

    /**
     * 获取版权申请详细信息
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:application:query')")
    @GetMapping(value = "/{applyId}")
    public AjaxResult getInfo(@PathVariable("applyId") Long applyId)
    {
        return success(copyrightApplicationService.selectCopyrightApplicationByApplyId(applyId));
    }

    /**
     * 新增版权申请
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:application:add')")
    @Log(title = "版权申请", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SysCopyrightApplication copyrightApplication)
    {
        return toAjax(copyrightApplicationService.insertCopyrightApplication(copyrightApplication));
    }

    /**
     * 修改版权申请
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:application:edit')")
    @Log(title = "版权申请", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SysCopyrightApplication copyrightApplication)
    {
        return toAjax(copyrightApplicationService.updateCopyrightApplication(copyrightApplication));
    }

    /**
     * 删除版权申请
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:application:remove')")
    @Log(title = "版权申请", businessType = BusinessType.DELETE)
    @DeleteMapping("/{applyIds}")
    public AjaxResult remove(@PathVariable Long[] applyIds)
    {
        return toAjax(copyrightApplicationService.deleteCopyrightApplicationByApplyIds(applyIds));
    }

    /**
     * 提交版权申请到版权中心配置页（分配到指定版权中心）
     * 
     * @param applyId 申请ID
     * @param configId 版权中心配置ID（前端选择）
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:application:submit')")
    @Log(title = "提交申请到版权中心", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{applyId}/to/{configId}")
    public AjaxResult submitToCenter(@PathVariable Long applyId, @PathVariable Long configId)
    {
        return toAjax(copyrightApplicationService.submitToCenterConfig(applyId, configId));
    }
    
    /**
     * 从版权中心配置页提交到第三方API
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:application:submit')")
    @Log(title = "提交到第三方版权中心", businessType = BusinessType.UPDATE)
    @PostMapping("/submitToApi/{applyId}")
    public AjaxResult submitToThirdPartyApi(@PathVariable Long applyId)
    {
        return toAjax(copyrightApplicationService.submitApplicationToCenter(applyId));
    }

    /**
     * 查询申请状态
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:application:query')")
    @GetMapping("/queryStatus/{applyId}")
    public AjaxResult queryStatus(@PathVariable Long applyId)
    {
        copyrightApplicationService.syncApplicationStatus(applyId);
        return success(copyrightApplicationService.selectCopyrightApplicationByApplyId(applyId));
    }

    /**
     * 审核版权申请（pending → approved/rejected）
     * 
     * @param applyId 申请ID
     * @param application 包含审核结果：status（approved/rejected）、rejectReason（驳回时必填）
     */
    @PreAuthorize("@ss.hasPermi('content:copyright:application:approve')")
    @Log(title = "审核版权申请", businessType = BusinessType.UPDATE)
    @PostMapping("/{applyId}/review")
    public AjaxResult reviewApplication(@PathVariable Long applyId, @RequestBody SysCopyrightApplication application)
    {
        return toAjax(copyrightApplicationService.reviewApplication(applyId, application.getStatus(), application.getRejectReason()));
    }
}
