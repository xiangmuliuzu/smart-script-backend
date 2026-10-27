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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.service.TradeOrderService;

/**
 * APP端订单接口（C交易域）。
 *
 * 路径：/api/v1/trade/order/*
 * 鉴权：由 APP 域的 Spring Security 链完成（/api/v1/trade/** 已配置）。
 *
 * 订单由接受报价自动生成（见 AppTradeQuoteController.accept），
 * 本 Controller 提供查询与状态流转。
 * 状态机：confirmed(签合同) →双方签署完成自动→ paying →买方付款→ delivering →买方确认收货→ completed。
 */
@RestController
@RequestMapping("/api/v1/trade/order")
public class AppTradeOrderController
{
    private final TradeOrderService orderService;

    public AppTradeOrderController(TradeOrderService orderService)
    {
        this.orderService = orderService;
    }

    /**
     * 订单列表（按角色 + 状态筛选，分页）。
     *
     * @param role     buyer / seller（必传，决定查买方还是卖方订单）
     * @param status   可选，订单状态精确匹配
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     */
    @GetMapping("/list")
    public AppApiResponse<Map<String, Object>> list(
            @RequestParam String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        Long currentUserId = currentUserId();

        SysOrder query = new SysOrder();
        if ("buyer".equals(role))
        {
            query.setBuyerId(currentUserId);
        }
        else if ("seller".equals(role))
        {
            query.setSellerId(currentUserId);
        }
        else
        {
            return AppApiResponse.fail(400, "role 参数无效，须为 buyer 或 seller");
        }

        if (status != null && !status.isEmpty())
        {
            query.setStatus(status);
        }
        if (keyword != null && !keyword.isEmpty())
        {
            query.setKeyword(keyword.trim());
        }

        // PageHelper 分页（Mapper 层 LIMIT/OFFSET，不再全量查再截取）
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        PageHelper.startPage(safePageNum, safePageSize);
        List<SysOrder> pageData = orderService.selectOrderList(query);
        PageInfo<SysOrder> pageInfo = new PageInfo<>(pageData);

        List<Map<String, Object>> rows = pageData.stream().map(this::toAppMap).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("total", pageInfo.getTotal());
        result.put("rows", rows);
        return AppApiResponse.ok(result);
    }

    /**
     * 订单详情。校验当前用户是该订单的买方或卖方。
     */
    @GetMapping("/{orderId}")
    public AppApiResponse<Map<String, Object>> detail(@PathVariable Long orderId)
    {
        Long currentUserId = currentUserId();
        SysOrder order = orderService.selectOrderById(orderId);
        if (order == null)
        {
            return AppApiResponse.fail(404, "订单不存在");
        }
        if (!currentUserId.equals(order.getBuyerId()) && !currentUserId.equals(order.getSellerId()))
        {
            return AppApiResponse.fail(403, "无权查看该订单");
        }

        Map<String, Object> data = toAppMap(order);
        return AppApiResponse.ok(data);
    }

    /**
     * 买方付款。订单 paying → delivering。
     */
    @PostMapping("/{orderId}/pay")
    public AppApiResponse<Void> pay(@PathVariable Long orderId)
    {
        Long currentUserId = currentUserId();
        SysOrder order = orderService.selectOrderById(orderId);
        if (order == null)
        {
            return AppApiResponse.fail(404, "订单不存在");
        }
        if (!currentUserId.equals(order.getBuyerId()))
        {
            return AppApiResponse.fail(403, "只有买方可以付款");
        }
        try
        {
            orderService.payOrder(orderId, currentUserId);
            return AppApiResponse.ok(null);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 取消订单。仅「合同双方签署完成前」（订单 confirmed）可由买卖双方任一方取消；签后锁定。
     */
    @PostMapping("/{orderId}/cancel")
    public AppApiResponse<Void> cancel(@PathVariable Long orderId,
                                       @RequestParam(value = "reason", required = false) String reason)
    {
        Long currentUserId = currentUserId();
        SysOrder order = orderService.selectOrderById(orderId);
        if (order == null)
        {
            return AppApiResponse.fail(404, "订单不存在");
        }
        if (!currentUserId.equals(order.getBuyerId()) && !currentUserId.equals(order.getSellerId()))
        {
            return AppApiResponse.fail(403, "只有交易双方可以取消订单");
        }
        try
        {
            orderService.cancelOrder(orderId, currentUserId, reason);
            return AppApiResponse.ok(null);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 买方确认收货。订单 delivering → completed，写入作者收益。
     */
    @PostMapping("/{orderId}/complete")
    public AppApiResponse<Void> complete(@PathVariable Long orderId)
    {
        Long currentUserId = currentUserId();
        SysOrder order = orderService.selectOrderById(orderId);
        if (order == null)
        {
            return AppApiResponse.fail(404, "订单不存在");
        }
        if (!currentUserId.equals(order.getBuyerId()))
        {
            return AppApiResponse.fail(403, "只有买方可以确认收货");
        }
        try
        {
            orderService.completeOrder(orderId, currentUserId);
            return AppApiResponse.ok(null);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    private Long currentUserId()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AppIdentityContext identity)
        {
            return identity.getUserId();
        }
        throw new RuntimeException("未登录或身份上下文异常");
    }

    /**
     * SysOrder → APP 端 Map（金额转分，公共方法避免 list/detail 重复）。
     */
    private Map<String, Object> toAppMap(SysOrder o)
    {
        Map<String, Object> m = new HashMap<>();
        m.put("orderId", o.getOrderId());
        m.put("orderNo", o.getOrderNo());
        m.put("inquiryId", o.getInquiryId());
        m.put("quoteId", o.getQuoteId());
        m.put("workId", o.getWorkId());
        m.put("buyerId", o.getBuyerId());
        m.put("sellerId", o.getSellerId());
        m.put("amount", o.getTotalAmount() != null
                ? o.getTotalAmount().multiply(new BigDecimal("100")).longValue() : null);
        m.put("licenseType", o.getLicenseType());
        m.put("status", o.getStatus());
        m.put("cancelReason", o.getRemark());
        m.put("createdAt", o.getCreatedAt());
        m.put("updatedAt", o.getUpdatedAt());
        m.put("workTitle", o.getWorkTitle());
        m.put("workCover", o.getWorkCover());
        m.put("buyerName", o.getBuyerName());
        m.put("sellerName", o.getSellerName());
        return m;
    }
}
