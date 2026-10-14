package com.smartscript.platform.trade.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.smartscript.platform.user.security.AppIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 * 本 Controller 只提供查询和取消。
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

        List<SysOrder> all = orderService.selectOrderList(query);

        // 手动分页
        int total = all.size();
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<SysOrder> pageData = (fromIndex < total) ? all.subList(fromIndex, toIndex) : List.of();

        // 金额转分（totalAmount 为 BigDecimal 元，APP 端用分）
        List<Map<String, Object>> rows = pageData.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("orderId", o.getOrderId());
            m.put("orderNo", o.getOrderNo());
            m.put("inquiryId", o.getInquiryId());
            m.put("quoteId", o.getQuoteId());
            m.put("workId", o.getWorkId());
            m.put("buyerId", o.getBuyerId());
            m.put("sellerId", o.getSellerId());
            m.put("amount", o.getTotalAmount() != null
                    ? o.getTotalAmount().multiply(new java.math.BigDecimal("100")).intValue() : null);
            m.put("licenseType", o.getLicenseType());
            m.put("status", o.getStatus());
            m.put("cancelReason", o.getRemark());
            m.put("createdAt", o.getCreatedAt());
            m.put("updatedAt", o.getUpdatedAt());
            m.put("workTitle", o.getWorkTitle());
            m.put("buyerName", o.getBuyerName());
            m.put("sellerName", o.getSellerName());
            return m;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
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

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getOrderId());
        data.put("orderNo", order.getOrderNo());
        data.put("inquiryId", order.getInquiryId());
        data.put("quoteId", order.getQuoteId());
        data.put("workId", order.getWorkId());
        data.put("buyerId", order.getBuyerId());
        data.put("sellerId", order.getSellerId());
        data.put("amount", order.getTotalAmount() != null
                ? order.getTotalAmount().multiply(new java.math.BigDecimal("100")).intValue() : null);
        data.put("licenseType", order.getLicenseType());
        data.put("status", order.getStatus());
        data.put("cancelReason", order.getRemark());
        data.put("createdAt", order.getCreatedAt());
        data.put("updatedAt", order.getUpdatedAt());
        data.put("workTitle", order.getWorkTitle());
        data.put("buyerName", order.getBuyerName());
        data.put("sellerName", order.getSellerName());
        return AppApiResponse.ok(data);
    }

    /**
     * 取消订单。仅 confirmed 状态可取消，仅买方可操作。
     */
    @PutMapping("/{orderId}/cancel")
    public AppApiResponse<Void> cancel(@PathVariable Long orderId, @RequestBody(required = false) Map<String, String> body)
    {
        Long currentUserId = currentUserId();
        SysOrder order = orderService.selectOrderById(orderId);
        if (order == null)
        {
            return AppApiResponse.fail(404, "订单不存在");
        }
        if (!currentUserId.equals(order.getBuyerId()))
        {
            return AppApiResponse.fail(403, "只有买方可以取消订单");
        }

        String reason = (body != null) ? body.get("reason") : null;
        try
        {
            orderService.cancelOrder(orderId, reason, currentUserId, String.valueOf(currentUserId));
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
}
