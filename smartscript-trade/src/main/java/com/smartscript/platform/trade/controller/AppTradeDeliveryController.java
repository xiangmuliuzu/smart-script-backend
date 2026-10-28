package com.smartscript.platform.trade.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.trade.domain.SysDelivery;
import com.smartscript.platform.trade.service.TradeDeliveryService;
import com.smartscript.platform.user.security.AppIdentityContext;

/**
 * APP 版权交割接口。
 * 路径：/api/v1/trade/delivery/*
 */
@RestController
@RequestMapping("/api/v1/trade/delivery")
public class AppTradeDeliveryController
{
    private final TradeDeliveryService deliveryService;

    public AppTradeDeliveryController(TradeDeliveryService deliveryService)
    {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/list")
    public AppApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        Long userId = currentUserId();
        deliveryService.ensureDeliveriesForUser(userId);
        SysDelivery query = new SysDelivery();
        if ("buyer".equals(role))
        {
            query.setBuyerId(userId);
        }
        else if ("seller".equals(role))
        {
            query.setSellerId(userId);
        }
        else
        {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            query.setParams(params);
        }
        if (status != null && !status.isEmpty())
        {
            query.setStatus(status);
        }

        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        PageHelper.startPage(safePage, safeSize);
        List<SysDelivery> list = deliveryService.selectDeliveryList(query);
        PageInfo<SysDelivery> pageInfo = new PageInfo<>(list);

        List<Map<String, Object>> rows = list.stream().map(deliveryService::toAppMap).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("total", pageInfo.getTotal());
        result.put("list", rows);
        return AppApiResponse.ok(result);
    }

    @GetMapping("/{deliveryId}")
    public AppApiResponse<Map<String, Object>> detail(@PathVariable Long deliveryId)
    {
        Long userId = currentUserId();
        SysDelivery delivery = deliveryService.selectDeliveryById(deliveryId);
        if (delivery == null)
        {
            return AppApiResponse.fail(404, "交割单不存在");
        }
        if (!userId.equals(delivery.getBuyerId()) && !userId.equals(delivery.getSellerId()))
        {
            return AppApiResponse.fail(403, "无权查看该交割单");
        }
        return AppApiResponse.ok(deliveryService.toAppMap(delivery));
    }

    @GetMapping("/by-order/{orderId}")
    public AppApiResponse<Map<String, Object>> byOrder(@PathVariable Long orderId)
    {
        Long userId = currentUserId();
        SysDelivery delivery = deliveryService.selectDeliveryByOrderId(orderId);
        if (delivery == null)
        {
            return AppApiResponse.fail(404, "该订单暂无交割单");
        }
        if (!userId.equals(delivery.getBuyerId()) && !userId.equals(delivery.getSellerId()))
        {
            return AppApiResponse.fail(403, "无权查看该交割单");
        }
        return AppApiResponse.ok(deliveryService.toAppMap(delivery));
    }

    @PostMapping("/{deliveryId}/upload")
    @SuppressWarnings("unchecked")
    public AppApiResponse<Map<String, Object>> upload(
            @PathVariable Long deliveryId,
            @RequestBody Map<String, Object> body)
    {
        Long userId = currentUserId();
        try
        {
            List<Map<String, Object>> files = null;
            if (body != null && body.get("files") instanceof List<?> raw)
            {
                files = (List<Map<String, Object>>) raw;
            }
            String remark = body != null && body.get("remark") != null
                    ? String.valueOf(body.get("remark")) : null;
            SysDelivery updated = deliveryService.uploadDelivery(deliveryId, userId, files, remark);
            return AppApiResponse.ok(deliveryService.toAppMap(updated));
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping("/{deliveryId}/confirm")
    public AppApiResponse<Map<String, Object>> confirm(@PathVariable Long deliveryId)
    {
        Long userId = currentUserId();
        try
        {
            SysDelivery updated = deliveryService.confirmDelivery(deliveryId, userId);
            return AppApiResponse.ok(deliveryService.toAppMap(updated));
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
