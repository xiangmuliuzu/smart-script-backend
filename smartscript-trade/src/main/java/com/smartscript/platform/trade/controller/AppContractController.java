package com.smartscript.platform.trade.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.service.TradeContractService;
import com.smartscript.platform.trade.service.TradeOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * APP 端合同接口。
 * <p>
 * 基础路径：/api/v1/trade/contracts
 * <ul>
 *   <li>GET /{orderId} — 按订单取合同详情</li>
 *   <li>POST /{contractId}/sign — 签署合同（multipart：signatureImage 可选、sealImage 可选）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/trade/contracts")
public class AppContractController
{
    @Autowired
    private TradeContractService contractService;

    @Autowired
    private TradeOrderService orderService;

    /**
     * 按订单 ID 取合同详情。
     */
    @GetMapping("/{orderId}")
    public AjaxResult getByOrderId(@PathVariable Long orderId)
    {
        try
        {
            // 仅订单买方/卖方可查看合同（含双方签名/印章与金额），与订单详情接口权限口径一致
            Long currentUserId = currentUserId();
            SysOrder order = orderService.selectOrderById(orderId);
            if (order == null)
            {
                return error("订单不存在");
            }
            if (!currentUserId.equals(order.getBuyerId()) && !currentUserId.equals(order.getSellerId()))
            {
                return error("您不是该订单的买方或卖方");
            }

            Map<String, Object> contract = contractService.getByOrderId(orderId);
            if (contract == null)
            {
                return error("该订单尚未生成合同");
            }
            return success(contract);
        }
        catch (Exception e)
        {
            return error(e.getMessage());
        }
    }

    /**
     * 签署合同。
     * <p>
     * 请求参数（multipart/form-data）：
     * <ul>
     *   <li>signatureImage — 手写签名图片（可选）</li>
     *   <li>sealImage — 印章图片（可选）</li>
     * </ul>
     * 至少传一项。
     */
    @PostMapping("/{contractId}/sign")
    public AjaxResult sign(@PathVariable Long contractId,
                           @RequestParam(value = "signatureImage", required = false) MultipartFile signatureImage,
                           @RequestParam(value = "sealImage", required = false) MultipartFile sealImage)
    {
        try
        {
            Map<String, Object> result = contractService.sign(contractId, signatureImage, sealImage);
            return success(result);
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
        catch (Exception e)
        {
            return error("签署失败：" + e.getMessage());
        }
    }

    private AjaxResult success(Object data)
    {
        AjaxResult result = new AjaxResult();
        result.put("code", 200);
        result.put("message", "操作成功");
        result.put("data", data);
        return result;
    }

    private AjaxResult error(String message)
    {
        AjaxResult result = new AjaxResult();
        result.put("code", 500);
        result.put("message", message);
        return result;
    }

    /**
     * 取当前登录用户 ID（APP 链路 principal 为 AppIdentityContext）。
     */
    private Long currentUserId()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof com.smartscript.platform.user.security.AppIdentityContext identity)
        {
            return identity.getUserId();
        }
        throw new RuntimeException("未登录或身份上下文异常");
    }
}
