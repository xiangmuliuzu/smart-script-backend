package com.ruoyi.web.controller.pc;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.web.service.pc.SettlementManageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 结算管理（与 App 同源 sys_income_settle）。
 * 列表 / 明细 / 处理作者异议；确认入账由作者在 App 完成。
 */
@RestController
@RequestMapping("/api/v1/admin/copyright/settlement")
public class SettlementManageController extends BaseController {

    private final SettlementManageService settlementService;

    public SettlementManageController(SettlementManageService settlementService) {
        this.settlementService = settlementService;
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:settlement:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String status,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "10") int pageSize) {
        int page = Math.max(1, pageNum);
        int size = Math.min(200, Math.max(1, pageSize));
        List<Map<String, Object>> rows = settlementService.list(status, startDate, endDate, keyword, page, size);
        TableDataInfo result = new TableDataInfo(rows, settlementService.count(status, startDate, endDate, keyword));
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        return result;
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:settlement:query')")
    @GetMapping("/{settlementId}")
    public AjaxResult getInfo(@PathVariable Long settlementId) {
        try {
            return success(settlementService.get(settlementId));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    /**
     * 处理作者异议：须填处理说明，状态回到 pending，由作者在 App 再次确认。
     */
    @PreAuthorize("@ss.hasPermi('smartscript:copyright:settlement:handle')")
    @Log(title = "结算异议处理", businessType = BusinessType.UPDATE)
    @PostMapping("/{settlementId}/handle")
    public AjaxResult handleAbnormal(@PathVariable Long settlementId, @RequestBody Map<String, Object> params) {
        try {
            String remark = params != null && params.get("remark") != null
                    ? String.valueOf(params.get("remark")) : null;
            Boolean markAsAbnormal = null;
            if (params != null && params.get("markAsAbnormal") instanceof Boolean) {
                markAsAbnormal = (Boolean) params.get("markAsAbnormal");
            }
            settlementService.handleAbnormal(settlementId, remark, markAsAbnormal);
            return success("处理成功，已回到待确认，请作者在 App 再次确认入账");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }
}
