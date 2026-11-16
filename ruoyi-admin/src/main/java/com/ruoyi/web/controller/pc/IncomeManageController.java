package com.ruoyi.web.controller.pc;

import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.web.service.pc.IncomeManageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 个人收益管理（只读核查）。
 * 路径：/api/v1/admin/copyright/income/*
 */
@RestController
@RequestMapping("/api/v1/admin/copyright/income")
public class IncomeManageController extends BaseController {

    private final IncomeManageService incomeManageService;

    public IncomeManageController(IncomeManageService incomeManageService) {
        this.incomeManageService = incomeManageService;
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:income:list')")
    @GetMapping("/accounts")
    public TableDataInfo listAccounts(@RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize) {
        int page = Math.max(1, pageNum);
        int size = Math.min(200, Math.max(1, pageSize));
        List<Map<String, Object>> rows = incomeManageService.listAccounts(keyword, page, size);
        TableDataInfo result = new TableDataInfo(rows, incomeManageService.countAccounts(keyword));
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        return result;
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:income:query')")
    @GetMapping("/accounts/{userId}")
    public AjaxResult getAccount(@PathVariable Long userId) {
        try {
            return success(incomeManageService.getAccount(userId));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:income:query')")
    @GetMapping("/accounts/{userId}/bills")
    public TableDataInfo listBills(@PathVariable Long userId,
                                   @RequestParam(required = false) String billType,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "10") int pageSize) {
        try {
            int page = Math.max(1, pageNum);
            int size = Math.min(200, Math.max(1, pageSize));
            List<Map<String, Object>> rows = incomeManageService.listBills(userId, billType, keyword, page, size);
            TableDataInfo result = new TableDataInfo(rows, incomeManageService.countBills(userId, billType, keyword));
            result.setCode(HttpStatus.SUCCESS);
            result.setMsg("查询成功");
            return result;
        } catch (IllegalArgumentException e) {
            TableDataInfo fail = new TableDataInfo();
            fail.setCode(HttpStatus.ERROR);
            fail.setMsg(e.getMessage());
            fail.setRows(List.of());
            fail.setTotal(0);
            return fail;
        }
    }
}
