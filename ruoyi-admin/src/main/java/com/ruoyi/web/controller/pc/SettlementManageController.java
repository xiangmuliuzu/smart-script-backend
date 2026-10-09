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

@RestController
@RequestMapping("/pc/copyright/settlement")
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

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:settlement:calculate')")
    @Log(title = "批量核算", businessType = BusinessType.INSERT)
    @PostMapping("/calculate")
    public AjaxResult calculate(@RequestBody Map<String, Object> params) {
        try {
            settlementService.calculate(params);
            return success("核算任务已提交");
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:settlement:handle')")
    @Log(title = "处理异常", businessType = BusinessType.UPDATE)
    @PostMapping("/{settlementId}/handle")
    public AjaxResult handleAbnormal(@PathVariable Long settlementId, @RequestBody Map<String, Object> params) {
        try {
            String remark = (String) params.get("remark");
            Boolean markAsAbnormal = params.containsKey("markAsAbnormal") ? (Boolean) params.get("markAsAbnormal") : null;
            settlementService.handleAbnormal(settlementId, remark, markAsAbnormal);
            return success("处理成功");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:settlement:handle')")
    @Log(title = "确认结算", businessType = BusinessType.UPDATE)
    @PostMapping("/{settlementId}/settle")
    public AjaxResult confirmSettle(@PathVariable Long settlementId) {
        try {
            settlementService.confirmSettle(settlementId);
            return success("确认成功");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }
}
