package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.smartscript.platform.content.domain.SysPromotionApplication;
import com.smartscript.platform.content.dto.PromotionAuditRequest;
import com.smartscript.platform.content.dto.PromotionGrantRequest;
import com.smartscript.platform.content.dto.PromotionPriceRequest;
import com.smartscript.platform.content.service.IPromotionService;

/**
 * 作品推广（PC 后台）
 *
 * 路由前缀 /api/v1/admin/content/promotion。
 * 权限统一 content:promotion:*（菜单 5411 及其按钮 5412~5418）。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/promotion")
public class SysPromotionController extends BaseController
{
    @Autowired
    private IPromotionService promotionService;

    /** 坑位总览：slot_type = banner / search / hot */
    @PreAuthorize("@ss.hasPermi('content:promotion:list')")
    @GetMapping("/slots/{slotType}")
    public AjaxResult slots(@PathVariable String slotType)
    {
        return success(promotionService.getSlotOverview(slotType));
    }

    /** 申请列表（分页） */
    @PreAuthorize("@ss.hasPermi('content:promotion:list')")
    @GetMapping("/applications/list")
    public TableDataInfo list(SysPromotionApplication query)
    {
        startPage();
        List<SysPromotionApplication> list = promotionService.selectApplicationList(query);
        return getDataTable(list);
    }

    /** 申请详情 */
    @PreAuthorize("@ss.hasPermi('content:promotion:query')")
    @GetMapping("/applications/{applyId}")
    public AjaxResult detail(@PathVariable Long applyId)
    {
        return success(promotionService.selectApplicationById(applyId));
    }

    /** 审核 */
    @PreAuthorize("@ss.hasPermi('content:promotion:audit')")
    @Log(title = "作品推广", businessType = BusinessType.UPDATE)
    @PostMapping("/applications/{applyId}/audit")
    public AjaxResult audit(@PathVariable Long applyId, @RequestBody PromotionAuditRequest request)
    {
        if (request == null || request.getApproved() == null)
        {
            return error("请选择审核结果");
        }
        try
        {
            return toAjax(promotionService.audit(applyId, request.getApproved(),
                    request.getRejectReason(), getUsername()));
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
    }

    /** 下架 / 取消投放 */
    @PreAuthorize("@ss.hasPermi('content:promotion:audit')")
    @Log(title = "作品推广", businessType = BusinessType.UPDATE)
    @PostMapping("/applications/{applyId}/offline")
    public AjaxResult offline(@PathVariable Long applyId)
    {
        try
        {
            return toAjax(promotionService.offline(applyId, getUsername()));
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
    }

    /** 免费直上推荐（不收费） */
    @PreAuthorize("@ss.hasPermi('content:promotion:grant')")
    @Log(title = "作品推广", businessType = BusinessType.INSERT)
    @PostMapping("/applications/grant")
    public AjaxResult grant(@RequestBody PromotionGrantRequest request)
    {
        try
        {
            return success(promotionService.grant(request, getUsername()));
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
    }

    /** 位次单价与启停配置 */
    @PreAuthorize("@ss.hasPermi('content:promotion:price')")
    @Log(title = "作品推广", businessType = BusinessType.UPDATE)
    @PutMapping("/slots/price")
    public AjaxResult updatePrice(@RequestBody PromotionPriceRequest request)
    {
        try
        {
            return toAjax(promotionService.updatePrices(request));
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
    }
}
