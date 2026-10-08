package com.ruoyi.web.controller.pc;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.content.domain.SysCopyrightSeal;
import com.smartscript.platform.content.domain.SysCopyrightSealLog;
import com.smartscript.platform.content.service.ISysCopyrightSealService;

/** PC 版权印章审核及启停管理。 */
@RestController
@RequestMapping("/api/v1/admin/copyright/seals")
public class CopyrightSealReviewController extends BaseController
{
    @Autowired
    private ISysCopyrightSealService sealService;

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:seals')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String sealStatus, @RequestParam(required = false) String keyword)
    {
        String safeReviewStatus = "pending".equals(reviewStatus) || "approved".equals(reviewStatus)
                || "rejected".equals(reviewStatus) ? reviewStatus : null;
        String safeSealStatus = "disabled".equals(sealStatus) || "enabled".equals(sealStatus)
                || "abnormal".equals(sealStatus) ? sealStatus : null;
        String safeKeyword = StringUtils.left(StringUtils.trimToNull(keyword), 100);
        startPage();
        List<SysCopyrightSeal> rows = sealService.selectSealList(safeReviewStatus, safeSealStatus, safeKeyword);
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:seals')")
    @GetMapping("/{sealId}")
    public AjaxResult detail(@PathVariable Long sealId)
    {
        SysCopyrightSeal seal = sealService.selectSealById(sealId);
        return seal == null ? AjaxResult.error("印章申请不存在") : AjaxResult.success(seal);
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:seals')")
    @GetMapping("/{sealId}/logs")
    public AjaxResult logs(@PathVariable Long sealId)
    {
        if (sealService.selectSealById(sealId) == null)
        {
            return AjaxResult.error("印章申请不存在");
        }
        List<SysCopyrightSealLog> logs = sealService.selectSealLogs(sealId);
        return AjaxResult.success(logs);
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:seals:audit')")
    @Log(title = "版权印章审核", businessType = BusinessType.UPDATE)
    @PostMapping("/{sealId}/review")
    public AjaxResult review(@PathVariable Long sealId, @RequestBody Map<String, String> body)
    {
        String action = body == null ? null : body.get("action");
        String reason = body == null ? null : body.get("reason");
        if (!"approve".equals(action) && !"reject".equals(action))
        {
            return AjaxResult.error("审核操作无效");
        }
        if ("reject".equals(action) && StringUtils.isBlank(reason))
        {
            return AjaxResult.error("驳回时必须填写原因");
        }
        if (reason != null && reason.trim().length() > 500)
        {
            return AjaxResult.error("审核原因不能超过500个字符");
        }
        boolean updated = sealService.reviewSeal(sealId, action, reason, SecurityUtils.getUserId(),
                SecurityUtils.getUsername());
        return updated ? AjaxResult.success() : AjaxResult.error("申请已处理或不存在，请刷新后重试");
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:seals:status')")
    @Log(title = "版权印章异常处理", businessType = BusinessType.UPDATE)
    @PostMapping("/{sealId}/resolve-abnormal")
    public AjaxResult resolveAbnormal(@PathVariable Long sealId, @RequestBody Map<String, String> body)
    {
        String reason = body == null ? null : body.get("reason");
        if (StringUtils.isBlank(reason) || reason.trim().length() > 500)
        {
            return AjaxResult.error("异常处理原因必填且不能超过500个字符");
        }
        boolean updated = sealService.resolveAbnormalSeal(sealId, reason, SecurityUtils.getUserId(),
                SecurityUtils.getUsername());
        return updated ? AjaxResult.success() : AjaxResult.error("仅审核通过且处于异常状态的印章可处理");
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:seals:status')")
    @Log(title = "版权印章状态变更", businessType = BusinessType.UPDATE)
    @PutMapping("/{sealId}/status")
    public AjaxResult updateStatus(@PathVariable Long sealId, @RequestBody Map<String, String> body)
    {
        String targetStatus = body == null ? null : body.get("status");
        if (!"enabled".equals(targetStatus) && !"disabled".equals(targetStatus))
        {
            return AjaxResult.error("印章状态无效");
        }
        boolean updated = sealService.updateSealStatus(sealId, targetStatus, SecurityUtils.getUserId(),
                SecurityUtils.getUsername());
        return updated ? AjaxResult.success() : AjaxResult.error("仅审核通过的印章可变更状态，请刷新后重试");
    }
}
