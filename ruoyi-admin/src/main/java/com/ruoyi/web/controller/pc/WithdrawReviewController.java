package com.ruoyi.web.controller.pc;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.web.service.pc.WithdrawReviewService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pc/copyright/withdraw")
public class WithdrawReviewController extends BaseController {
    private static final List<String> EXPORT_COLUMNS = List.of("提现编号", "用户编号", "用户名", "登录账号", "提现金额",
            "手续费", "实际到账", "提现方式", "账户名", "账号", "开户行", "状态", "审核时间", "审核意见", "申请时间", "打款时间");
    private static final List<String> EXPORT_FIELDS = List.of("withdrawNo", "userId", "userName", "userAccount", "amount",
            "fee", "actualAmount", "withdrawType", "accountName", "accountNo", "bankName", "status", "reviewTime",
            "reviewOpinion", "createdAt", "payTime");

    private final WithdrawReviewService withdrawService;

    public WithdrawReviewController(WithdrawReviewService withdrawService) {
        this.withdrawService = withdrawService;
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:withdraw:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String status,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "10") int pageSize) {
        int page = Math.max(1, pageNum);
        int size = Math.min(200, Math.max(1, pageSize));
        List<Map<String, Object>> rows = withdrawService.list(status, keyword, page, size);
        TableDataInfo result = new TableDataInfo(rows, withdrawService.count(status, keyword));
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        return result;
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:withdraw:query')")
    @GetMapping("/{withdrawId}")
    public AjaxResult getInfo(@PathVariable Long withdrawId) {
        try {
            return success(withdrawService.get(withdrawId));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:withdraw:approve')")
    @Log(title = "提现审核", businessType = BusinessType.UPDATE)
    @PostMapping("/{withdrawId}/approve")
    public AjaxResult approve(@PathVariable Long withdrawId) {
        try {
            withdrawService.approve(withdrawId);
            return success("审核通过");
        } catch (IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:withdraw:reject')")
    @Log(title = "提现审核", businessType = BusinessType.UPDATE)
    @PostMapping("/{withdrawId}/reject")
    public AjaxResult reject(@PathVariable Long withdrawId, @RequestBody Map<String, String> params) {
        try {
            withdrawService.reject(withdrawId, params == null ? null : params.get("opinion"));
            return success("驳回成功");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:withdraw:freeze')")
    @Log(title = "提现冻结", businessType = BusinessType.UPDATE)
    @PostMapping("/{withdrawId}/freeze")
    public AjaxResult freeze(@PathVariable Long withdrawId) {
        try {
            withdrawService.freeze(withdrawId);
            return success("冻结成功");
        } catch (IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:withdraw:unfreeze')")
    @Log(title = "提现解冻", businessType = BusinessType.UPDATE)
    @PostMapping("/{withdrawId}/unfreeze")
    public AjaxResult unfreeze(@PathVariable Long withdrawId) {
        try {
            withdrawService.unfreeze(withdrawId);
            return success("解冻成功");
        } catch (IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:withdraw:export')")
    @Log(title = "提现记录导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = withdrawService.export(status, keyword);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=withdraw-records.csv");
        var output = response.getOutputStream();
        output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        output.write(csvRow(EXPORT_COLUMNS).getBytes(StandardCharsets.UTF_8));
        for (Map<String, Object> row : rows) {
            output.write(csvRow(EXPORT_FIELDS.stream().map(row::get).toList()).getBytes(StandardCharsets.UTF_8));
        }
        output.flush();
    }

    private static String csvRow(List<?> values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) row.append(',');
            Object value = values.get(i);
            String text = value == null ? "" : String.valueOf(value);
            String trimmed = text.stripLeading();
            if (!trimmed.isEmpty() && ("=+-@".indexOf(trimmed.charAt(0)) >= 0
                    || "\t\r\n".indexOf(trimmed.charAt(0)) >= 0)) text = "'" + text;
            row.append('"').append(text.replace("\"", "\"\"")).append('"');
        }
        return row.append("\r\n").toString();
    }
}
