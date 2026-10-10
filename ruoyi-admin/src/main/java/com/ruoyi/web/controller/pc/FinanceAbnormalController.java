package com.ruoyi.web.controller.pc;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.web.service.pc.FinanceAbnormalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pc/copyright/finance-abnormal")
public class FinanceAbnormalController extends BaseController {

    private final FinanceAbnormalService financeAbnormalService;

    public FinanceAbnormalController(FinanceAbnormalService financeAbnormalService) {
        this.financeAbnormalService = financeAbnormalService;
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:finance:list')")
    @GetMapping("/{type}/list")
    public TableDataInfo list(@PathVariable String type,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "10") int pageSize) {
        int page = Math.max(1, pageNum);
        int size = Math.min(200, Math.max(1, pageSize));
        List<Map<String, Object>> rows = financeAbnormalService.list(type, status, keyword, page, size);
        TableDataInfo result = new TableDataInfo(rows, financeAbnormalService.count(type, status, keyword));
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        return result;
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:finance:query')")
    @GetMapping("/{type}/{recordId}")
    public AjaxResult getDetail(@PathVariable String type,
                               @PathVariable Long recordId) {
        try {
            return success(financeAbnormalService.getDetail(recordId));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:finance:handle')")
    @Log(title = "处理财务异常", businessType = BusinessType.UPDATE)
    @PostMapping("/{type}/{recordId}/handle")
    public AjaxResult handle(@PathVariable String type,
                            @PathVariable Long recordId,
                            @RequestBody Map<String, String> params) {
        try {
            financeAbnormalService.handle(type, recordId, params.get("solution"), params.get("remark"));
            return success("处理成功");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }
}
