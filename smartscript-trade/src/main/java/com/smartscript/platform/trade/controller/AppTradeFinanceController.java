package com.smartscript.platform.trade.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.smartscript.platform.user.security.AppIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.trade.domain.SysBillRecord;
import com.smartscript.platform.trade.domain.SysInvoice;
import com.smartscript.platform.trade.service.TradeFinanceService;

/**
 * APP端财务接口（收益 / 待结算 / 提现 / 发票）。
 *
 * 路径：/api/v1/trade/finance/*
 * 鉴权：由 APP 域的 Spring Security 链完成（/api/v1/trade/** 已配置）。
 * 资金托管已退役，仅保留 /escrow/** 的 410 墓碑。
 */
@RestController
@RequestMapping("/api/v1/trade/finance")
public class AppTradeFinanceController
{
    /** 资金托管已随「任务发布收归平台运营」退役，用户侧托管端点统一返回此提示。 */
    private static final String ESCROW_OFFLINE_MSG =
            "资金托管已退役，交易款项由平台在订单结算时直接结算，不再提供托管查询入口";

    private final TradeFinanceService financeService;

    public AppTradeFinanceController(TradeFinanceService financeService)
    {
        this.financeService = financeService;
    }

    /* ========== 收益 ========== */

    /**
     * 收益总览。
     */
    @GetMapping("/income/overview")
    public AppApiResponse<Map<String, Object>> incomeOverview()
    {
        Long userId = currentUserId();
        Map<String, Object> overview = financeService.getIncomeOverview(userId);
        return AppApiResponse.ok(overview);
    }

    /**
     * 收益明细列表（账单流水，分页）。
     */
    @GetMapping("/income/detail")
    public AppApiResponse<Map<String, Object>> incomeDetail(
            @RequestParam(required = false) String billType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        Long userId = currentUserId();
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        PageHelper.startPage(safePage, safeSize);
        List<SysBillRecord> records = financeService.getBillRecordList(userId, billType);
        PageInfo<SysBillRecord> pageInfo = new PageInfo<>(records);

        List<Map<String, Object>> rows = records.stream().map(this::toBillMap).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("total", pageInfo.getTotal());
        result.put("rows", rows);
        return AppApiResponse.ok(result);
    }

    /**
     * 收入/提现统计。
     * period: week / month / year
     */
    @GetMapping("/income/stats")
    public AppApiResponse<Map<String, Object>> incomeStats(
            @RequestParam(defaultValue = "month") String period)
    {
        Long userId = currentUserId();
        try
        {
            return AppApiResponse.ok(financeService.getIncomeStats(userId, period));
        }
        catch (IllegalArgumentException ex)
        {
            return AppApiResponse.fail(400, ex.getMessage());
        }
    }

    /* ========== 待结算（到账前确认/异议） ========== */

    /**
     * 待结算列表。
     */
    @GetMapping("/income/settle/list")
    public AppApiResponse<Map<String, Object>> settleList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        Long userId = currentUserId();
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        long total = financeService.countSettleList(userId, status);
        List<Map<String, Object>> rows = financeService.getSettleList(userId, status, safePage, safeSize);
        for (Map<String, Object> row : rows)
        {
            toFen(row, "totalAmount");
            toFen(row, "platformFee");
            toFen(row, "sellerAmount");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        return AppApiResponse.ok(result);
    }

    /**
     * 待结算详情。
     */
    @GetMapping("/income/settle/{settleId}")
    public AppApiResponse<Map<String, Object>> settleDetail(@PathVariable Long settleId)
    {
        Long userId = currentUserId();
        try
        {
            Map<String, Object> detail = financeService.getSettleDetail(userId, settleId);
            toFen(detail, "totalAmount");
            toFen(detail, "platformFee");
            toFen(detail, "sellerAmount");
            return AppApiResponse.ok(detail);
        }
        catch (IllegalArgumentException ex)
        {
            return AppApiResponse.fail(404, ex.getMessage());
        }
    }

    /**
     * 作者确认结算并入账。
     */
    @PostMapping("/income/settle/{settleId}/confirm")
    public AppApiResponse<Map<String, Object>> settleConfirm(@PathVariable Long settleId)
    {
        Long userId = currentUserId();
        try
        {
            Map<String, Object> result = financeService.confirmSettle(userId, settleId);
            toFen(result, "totalAmount");
            toFen(result, "platformFee");
            toFen(result, "sellerAmount");
            return AppApiResponse.ok(result);
        }
        catch (IllegalArgumentException | IllegalStateException ex)
        {
            return AppApiResponse.fail(400, ex.getMessage());
        }
    }

    /**
     * 作者提出异议。
     * body: reason
     */
    @PostMapping("/income/settle/{settleId}/dispute")
    public AppApiResponse<Map<String, Object>> settleDispute(
            @PathVariable Long settleId, @RequestBody Map<String, String> body)
    {
        Long userId = currentUserId();
        try
        {
            String reason = body == null ? null : body.get("reason");
            Map<String, Object> result = financeService.disputeSettle(userId, settleId, reason);
            toFen(result, "totalAmount");
            toFen(result, "platformFee");
            toFen(result, "sellerAmount");
            return AppApiResponse.ok(result);
        }
        catch (IllegalArgumentException | IllegalStateException ex)
        {
            return AppApiResponse.fail(400, ex.getMessage());
        }
    }

    /**
     * 收益账单详情（含订单分成核算字段）。
     */
    @GetMapping("/income/detail/{billId}")
    public AppApiResponse<Map<String, Object>> incomeBillDetail(@PathVariable Long billId)
    {
        Long userId = currentUserId();
        try
        {
            Map<String, Object> detail = financeService.getBillDetail(userId, billId);
            toFen(detail, "amount");
            toFen(detail, "balanceAfter");
            toFen(detail, "totalAmount");
            toFen(detail, "platformFee");
            toFen(detail, "sellerAmount");
            detail.put("incomeSource", billSourceLabel(
                    detail.get("billType") != null ? detail.get("billType").toString() : null,
                    detail.get("relatedType") != null ? detail.get("relatedType").toString() : null));
            return AppApiResponse.ok(detail);
        }
        catch (IllegalArgumentException ex)
        {
            return AppApiResponse.fail(404, ex.getMessage());
        }
    }

    /* ========== 提现 ========== */

    /**
     * 发起提现申请。金额 ≤ 10000 元自动通过；超过 10000 元进入待审核。
     * <p>
     * body: amount, withdrawType(bank/alipay/wechat), accountName, accountNo, bankName?, clientToken?
     */
    @PostMapping("/withdraw/apply")
    public AppApiResponse<Map<String, Object>> withdrawApply(@RequestBody Map<String, Object> body)
    {
        Long userId = currentUserId();
        try
        {
            BigDecimal amount = body.get("amount") != null
                    ? new BigDecimal(body.get("amount").toString()) : null;
            Map<String, Object> result = financeService.applyWithdraw(
                    userId,
                    amount,
                    body.get("withdrawType") != null ? body.get("withdrawType").toString() : null,
                    body.get("accountName") != null ? body.get("accountName").toString() : null,
                    body.get("accountNo") != null ? body.get("accountNo").toString() : null,
                    body.get("bankName") != null ? body.get("bankName").toString() : null,
                    body.get("clientToken") != null ? body.get("clientToken").toString() : null);
            return AppApiResponse.ok(result);
        }
        catch (IllegalArgumentException | IllegalStateException ex)
        {
            return AppApiResponse.fail(400, ex.getMessage());
        }
    }

    /**
     * 我的提现记录（含失败/驳回原因 reviewOpinion）。
     */
    @GetMapping("/withdraw/list")
    public AppApiResponse<Map<String, Object>> withdrawList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        Long userId = currentUserId();
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        long total = financeService.countWithdrawList(userId, status);
        List<Map<String, Object>> rows = financeService.getWithdrawList(userId, status, safePage, safeSize);

        // 金额统一转分，与账单明细一致
        for (Map<String, Object> row : rows)
        {
            toFen(row, "amount");
            toFen(row, "fee");
            toFen(row, "actualAmount");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        return AppApiResponse.ok(result);
    }

    /* ========== 资金托管（已退役） ========== */

    /**
     * 托管记录列表：资金托管链路已随任务发布收归平台运营一并退役，仅保留 410 墓碑，
     * 用于提示仍缓存旧页面的客户端。历史 sys_escrow 数据只读留存，不再对外提供查询。
     */
    @GetMapping("/escrow/list")
    public AppApiResponse<Map<String, Object>> escrowList(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        return AppApiResponse.fail(410, ESCROW_OFFLINE_MSG);
    }

    /**
     * 托管详情：同 {@link #escrowList}，端点已退役。
     */
    @GetMapping("/escrow/{escrowId}")
    public AppApiResponse<Map<String, Object>> escrowDetail(@PathVariable Long escrowId)
    {
        return AppApiResponse.fail(410, ESCROW_OFFLINE_MSG);
    }

    /* ========== 发票 ========== */

    /**
     * 发票列表（分页 + 状态筛选）。
     */
    @GetMapping("/invoice/list")
    public AppApiResponse<Map<String, Object>> invoiceList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        Long userId = currentUserId();
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        PageHelper.startPage(safePage, safeSize);
        List<SysInvoice> invoices = financeService.getInvoiceList(userId, status);
        PageInfo<SysInvoice> pageInfo = new PageInfo<>(invoices);

        List<Map<String, Object>> rows = invoices.stream().map(this::toInvoiceMap).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("total", pageInfo.getTotal());
        result.put("rows", rows);
        return AppApiResponse.ok(result);
    }

    /**
     * 发票详情。
     */
    @GetMapping("/invoice/{invoiceId}")
    public AppApiResponse<Map<String, Object>> invoiceDetail(@PathVariable Long invoiceId)
    {
        Long userId = currentUserId();
        SysInvoice invoice = financeService.getInvoiceDetail(invoiceId);
        if (invoice == null)
        {
            return AppApiResponse.fail(404, "发票记录不存在");
        }
        if (!userId.equals(invoice.getApplicantId()))
        {
            return AppApiResponse.fail(403, "无权查看该发票");
        }
        return AppApiResponse.ok(toInvoiceMap(invoice));
    }

    /**
     * 申请发票。
     *
     * 开票金额由服务端按订单实付总额落地（见 TradeFinanceService.applyInvoice），
     * 客户端无需也不得传 amount。
     */
    @PostMapping("/invoice/apply")
    public AppApiResponse<Void> applyInvoice(@RequestBody Map<String, Object> body)
    {
        Long userId = currentUserId();

        Long orderId = body.get("orderId") != null ? Long.valueOf(body.get("orderId").toString()) : null;
        String invoiceType = (String) body.get("invoiceType");
        String title = (String) body.get("title");
        String taxNo = (String) body.get("taxNo");
        String email = (String) body.get("email");

        if (orderId == null || invoiceType == null || title == null)
        {
            return AppApiResponse.fail(400, "开票信息不完整，请检查后重试");
        }

        SysInvoice invoice = new SysInvoice();
        invoice.setOrderId(orderId);
        invoice.setApplicantId(userId);
        invoice.setInvoiceType(invoiceType);
        invoice.setTitle(title);
        invoice.setTaxNo(taxNo);
        invoice.setEmail(email);

        financeService.applyInvoice(invoice);
        return AppApiResponse.ok(null);
    }

    /**
     * 可开票订单列表（已完成且未开过发票）。
     */
    @GetMapping("/invoice/orders")
    public AppApiResponse<List<Map<String, Object>>> invoiceableOrders()
    {
        Long userId = currentUserId();
        List<Map<String, Object>> orders = financeService.getInvoiceableOrders(userId);
        // 金额转分
        for (Map<String, Object> o : orders)
        {
            Object amount = o.get("amount");
            if (amount instanceof BigDecimal bd)
            {
                o.put("amount", bd.multiply(new BigDecimal("100")).intValue());
            }
        }
        return AppApiResponse.ok(orders);
    }

    /* ========== 私有方法 ========== */

    private Long currentUserId()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AppIdentityContext identity)
        {
            return identity.getUserId();
        }
        throw new RuntimeException("未登录或身份上下文异常");
    }

    private Map<String, Object> toInvoiceMap(SysInvoice i)
    {
        Map<String, Object> m = new HashMap<>();
        m.put("invoiceId", i.getInvoiceId());
        m.put("invoiceNo", i.getInvoiceNo());
        m.put("orderId", i.getOrderId());
        m.put("applicantId", i.getApplicantId());
        m.put("invoiceType", i.getInvoiceType());
        m.put("email", i.getEmail());
        m.put("title", i.getTitle());
        m.put("taxNo", i.getTaxNo());
        m.put("amount", i.getAmount() != null
                ? i.getAmount().multiply(new BigDecimal("100")).intValue() : null);
        m.put("invoiceUrl", i.getInvoiceUrl());
        m.put("status", i.getStatus());
        m.put("rejectReason", i.getRejectReason());
        m.put("invoiceTime", i.getInvoiceTime());
        m.put("createdAt", i.getCreatedAt());
        m.put("orderNo", i.getOrderNo());
        m.put("workTitle", i.getWorkTitle());
        return m;
    }

    private Map<String, Object> toBillMap(SysBillRecord b)
    {
        Map<String, Object> m = new HashMap<>();
        m.put("billId", b.getBillId());
        m.put("billNo", b.getBillNo());
        m.put("billType", b.getBillType());
        m.put("amount", b.getAmount() != null
                ? b.getAmount().multiply(new BigDecimal("100")).intValue() : null);
        m.put("balanceAfter", b.getBalanceAfter() != null
                ? b.getBalanceAfter().multiply(new BigDecimal("100")).intValue() : null);
        m.put("relatedType", b.getRelatedType());
        m.put("relatedId", b.getRelatedId());
        m.put("remark", b.getRemark());
        m.put("createdAt", b.getCreatedAt());
        m.put("orderNo", b.getOrderNo());
        m.put("workTitle", b.getWorkTitle());
        m.put("settleStatus", b.getSettleStatus());
        // 任务逐轮放款的轮次序号，便于 App 在账单列表标出“第N轮”
        m.put("stageOrder", b.getStageOrder());
        // 收益来源文案，便于 App 直接展示
        m.put("incomeSource", billSourceLabel(b.getBillType(), b.getRelatedType()));
        return m;
    }

    /**
     * 收益来源文案。同为 income 流水，任务逐轮放款与订单货款必须区分开，
     * 否则创作者看到的“订单收入”其实是一轮验收款。
     */
    private static String billSourceLabel(String billType, String relatedType)
    {
        if (billType == null) return "未知";
        boolean fromDemandStage = "demand_stage".equals(relatedType);
        return switch (billType)
        {
            case "income" -> fromDemandStage ? "任务轮次收益" : "订单收入";
            case "withdraw" -> "提现支出";
            case "freeze" -> "资金冻结";
            case "settle" -> "分成结算";
            default -> billType;
        };
    }

    private static void toFen(Map<String, Object> row, String key)
    {
        Object value = row.get(key);
        if (value instanceof BigDecimal bd)
        {
            row.put(key, bd.multiply(new BigDecimal("100")).intValue());
        }
        else if (value instanceof Number n)
        {
            row.put(key, BigDecimal.valueOf(n.doubleValue()).multiply(new BigDecimal("100")).intValue());
        }
    }
}
