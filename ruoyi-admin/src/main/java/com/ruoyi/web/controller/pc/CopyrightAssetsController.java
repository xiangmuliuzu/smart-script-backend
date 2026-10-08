package com.ruoyi.web.controller.pc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.content.dto.CopyrightAssetDto;
import com.smartscript.platform.content.dto.CopyrightAssetQuery;
import com.smartscript.platform.content.service.ISysWorkService;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.service.TradeOrderService;

/** PC 版权资产查询与状态维护。 */
@RestController
@RequestMapping("/api/v1/admin/copyright/assets")
public class CopyrightAssetsController extends BaseController
{
    @Autowired
    private ISysWorkService workService;

    @Autowired
    private TradeOrderService orderService;

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:assets')")
    @GetMapping
    public TableDataInfo list(CopyrightAssetQuery query)
    {
        normalize(query);
        startPage();
        List<CopyrightAssetDto> list = workService.selectCopyrightAssetList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:assets')")
    @GetMapping("/{workId}")
    public AjaxResult detail(@PathVariable Long workId)
    {
        CopyrightAssetDto asset = workService.selectCopyrightAssetById(workId);
        if (asset == null)
        {
            return AjaxResult.error("版权资产不存在或已不可见");
        }
        return AjaxResult.success(asset);
    }

    /** 授权历史查询必须单独授权，且仅返回仍有效的版权资产。 */
    @PreAuthorize("@ss.hasPermi('smartscript:copyright:assets:history')")
    @GetMapping("/{workId}/authorization-history")
    public TableDataInfo authorizationHistory(@PathVariable Long workId)
    {
        if (workService.selectCopyrightAssetById(workId) == null)
        {
            return getDataTable(Collections.emptyList());
        }
        startPage();
        List<SysOrder> list = orderService.selectAuthorizationHistoryByWorkId(workId);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:assets:edit')")
    @Log(title = "版权资产上下架", businessType = BusinessType.UPDATE)
    @PutMapping("/{workId}/status")
    public AjaxResult updateStatus(@PathVariable Long workId, @RequestBody Map<String, String> body)
    {
        String status = body == null ? null : body.get("status");
        if (!"on_shelf".equals(status) && !"off_shelf".equals(status))
        {
            return AjaxResult.error("上架状态无效");
        }
        int rows = workService.updateCopyrightAssetStatus(workId, status, SecurityUtils.getUsername());
        return rows > 0 ? AjaxResult.success() : AjaxResult.error("资产不存在、已删除或状态未变化");
    }

    private void normalize(CopyrightAssetQuery query)
    {
        String keyword = StringUtils.trimToNull(query.getKeyword());
        query.setKeyword(keyword == null ? null : StringUtils.left(keyword, 100));
        if (!"on_shelf".equals(query.getStatus()) && !"off_shelf".equals(query.getStatus()))
        {
            query.setStatus(null);
        }
    }
}
