package com.smartscript.platform.trade.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.smartscript.platform.user.security.AppIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.domain.SysQuote;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.service.TradeInquiryService;
import com.smartscript.platform.trade.service.TradeQuoteService;
import com.smartscript.platform.user.service.ChatService;

/**
 * APP端报价/议价接口（C交易域）。
 *
 * 路径：/api/v1/trade/quote/*
 * 鉴权：由 APP 域的 Spring Security 链完成。
 *
 * 报价流程：
 *   1. 卖方对询盘提交报价（quoter_role=seller, status=pending）
 *   2. 买方可对卖方报价发起议价（quoter_role=buyer, status=pending_seller）
 *   3. 卖方接受买方议价（acceptQuote with operatorRole=seller）
 *   4. 卖方拒绝买方议价（rejectQuote with operatorRole=seller）
 *   5. 卖方可修改自己的报价（modifyQuote with operatorRole=seller）
 *   6. 买方可修改自己的议价（modifyQuote with operatorRole=buyer）
 *   7. 接受报价后自动生成订单
 */
@RestController
@RequestMapping("/api/v1/trade/quote")
public class AppTradeQuoteController
{
    private final TradeQuoteService quoteService;
    private final TradeInquiryService inquiryService;
    private final ChatService chatService;

    public AppTradeQuoteController(TradeQuoteService quoteService, TradeInquiryService inquiryService, ChatService chatService)
    {
        this.quoteService = quoteService;
        this.inquiryService = inquiryService;
        this.chatService = chatService;
    }

    /**
     * 报价列表（按询盘ID查询该询盘下的所有报价/议价记录）。
     */
    @GetMapping("/list")
    public AppApiResponse<Map<String, Object>> listByInquiry(@RequestParam Long inquiryId)
    {
        SysQuote query = new SysQuote();
        query.setInquiryId(inquiryId);
        List<SysQuote> list = quoteService.selectQuoteList(query);

        Map<String, Object> result = new HashMap<>();
        result.put("total", list.size());
        result.put("list", list);
        return AppApiResponse.ok(result);
    }

    /**
     * 报价详情。
     */
    @GetMapping("/{id}")
    public AppApiResponse<SysQuote> detail(@PathVariable Long id)
    {
        SysQuote quote = quoteService.selectQuoteById(id);
        if (quote == null)
        {
            return AppApiResponse.fail(404, "报价不存在");
        }
        return AppApiResponse.ok(quote);
    }

    /**
     * 查询某询盘下最新一条报价（用于聊天页议价卡片）。
     */
    @GetMapping("/latest")
    public AppApiResponse<SysQuote> latest(@RequestParam Long inquiryId)
    {
        SysQuote query = new SysQuote();
        query.setInquiryId(inquiryId);
        List<SysQuote> list = quoteService.selectQuoteList(query);
        if (list == null || list.isEmpty())
        {
            return AppApiResponse.ok(null);
        }
        // 查询按 created_at desc 排序，第一条即最新
        return AppApiResponse.ok(list.get(0));
    }

    /**
     * 卖方提交报价（分工条目 9）。
     *
     * 入参：inquiryId, price, licenseType, validDays, description
     * 卖方对询盘出价，产生 status=pending 的报价记录。
     */
    @PostMapping
    public AppApiResponse<SysQuote> create(@RequestBody SysQuote quote)
    {
        Long currentUserId = currentUserId();

        // 校验询盘存在且当前用户是卖方
        SysInquiry inquiry = inquiryService.selectInquiryById(quote.getInquiryId());
        if (inquiry == null)
        {
            return AppApiResponse.fail(404, "询盘不存在");
        }
        if (!currentUserId.equals(inquiry.getSellerId()))
        {
            return AppApiResponse.fail(403, "只有卖方可以提交报价");
        }

        quote.setSellerId(inquiry.getSellerId());
        quote.setQuoterId(currentUserId);
        quote.setCreateBy(String.valueOf(currentUserId));

        SysQuote created = quoteService.createQuote(quote);

        // 向关联的 TRADE 会话发送系统消息
        String amountYuan = formatYuan(created.getPrice());
        String licenseLabel = licenseLabel(created.getLicenseType());
        String notice = "[报价] 卖方报价 " + amountYuan + " | " + licenseLabel
                + (created.getValidDays() != null ? " | 有效期" + created.getValidDays() + "天" : "");
        chatService.sendNoticeByInquiryId(quote.getInquiryId(), notice);

        return AppApiResponse.ok(created);
    }

    /**
     * 买方发起议价（分工条目 10）。
     *
     * 入参：inquiryId, price, licenseType, description
     * 买方对询盘出价（counter-offer），产生 status=pending_seller 的报价记录。
     */
    @PostMapping("/counter-offer")
    public AppApiResponse<SysQuote> counterOffer(@RequestBody SysQuote quote)
    {
        Long currentUserId = currentUserId();

        // 校验询盘存在且当前用户是买方
        SysInquiry inquiry = inquiryService.selectInquiryById(quote.getInquiryId());
        if (inquiry == null)
        {
            return AppApiResponse.fail(404, "询盘不存在");
        }
        if (!currentUserId.equals(inquiry.getBuyerId()))
        {
            return AppApiResponse.fail(403, "只有买方可以发起议价");
        }

        quote.setSellerId(inquiry.getSellerId());
        quote.setQuoterId(currentUserId);
        quote.setCreateBy(String.valueOf(currentUserId));

        SysQuote created = quoteService.counterOffer(quote);

        // 向关联的 TRADE 会话发送系统消息
        String amountYuan = formatYuan(created.getPrice());
        String licenseLabel = licenseLabel(created.getLicenseType());
        String notice = "[议价] 买方议价 " + amountYuan + " | " + licenseLabel;
        chatService.sendNoticeByInquiryId(quote.getInquiryId(), notice);

        return AppApiResponse.ok(created);
    }

    /**
     * 修改报价/议价。
     *
     * 只能修改自己的报价：卖方改自己的报价，买方改自己的议价。
     */
    @PutMapping("/{id}")
    public AppApiResponse<Void> modify(@PathVariable Long id, @RequestBody SysQuote patch)
    {
        Long currentUserId = currentUserId();
        SysQuote quote = quoteService.selectQuoteById(id);
        if (quote == null)
        {
            return AppApiResponse.fail(404, "报价不存在");
        }
        if (!currentUserId.equals(quote.getQuoterId()))
        {
            return AppApiResponse.fail(403, "只能修改自己的报价");
        }

        // 根据报价方角色确定操作者角色
        String operatorRole = quote.getQuoterRole();
        quoteService.modifyQuote(id, patch, operatorRole);
        return AppApiResponse.ok();
    }

    /**
     * 接受报价。
     *
     * - 卖方报价(status=pending)：买方可接受 → 生成订单
     * - 买方议价(status=pending_seller)：卖方可接受 → 生成订单
     */
    @PutMapping("/{id}/accept")
    public AppApiResponse<Map<String, Object>> accept(@PathVariable Long id)
    {
        Long currentUserId = currentUserId();
        SysQuote quote = quoteService.selectQuoteById(id);
        if (quote == null)
        {
            return AppApiResponse.fail(404, "报价不存在");
        }

        // 查询询盘以确定买卖双方
        SysInquiry inquiry = inquiryService.selectInquiryById(quote.getInquiryId());
        if (inquiry == null)
        {
            return AppApiResponse.fail(404, "关联询盘不存在");
        }

        // 确定操作者角色
        String operatorRole;
        if ("pending".equals(quote.getStatus()) && currentUserId.equals(inquiry.getBuyerId()))
        {
            // 卖方报价(status=pending)，买方接受
            operatorRole = "buyer";
        }
        else if ("pending_seller".equals(quote.getStatus()) && currentUserId.equals(inquiry.getSellerId()))
        {
            // 买方议价(status=pending_seller)，卖方接受
            operatorRole = "seller";
        }
        else
        {
            return AppApiResponse.fail(403, "无权操作该报价");
        }

        SysOrder order = quoteService.acceptQuote(id, operatorRole);

        // 向关联的 TRADE 会话发送系统消息
        String amountYuan = formatYuan(quote.getPrice());
        chatService.sendNoticeByInquiryId(quote.getInquiryId(),
                "[接受] 报价已接受，成交金额 " + amountYuan + "，订单已生成");

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getOrderId());
        result.put("orderNo", order.getOrderNo());
        return AppApiResponse.ok(result);
    }

    /**
     * 拒绝报价。
     *
     * - 卖方报价(status=pending)：买方可拒绝
     * - 买方议价(status=pending_seller)：卖方可拒绝
     */
    @PutMapping("/{id}/reject")
    public AppApiResponse<Void> reject(@PathVariable Long id)
    {
        Long currentUserId = currentUserId();
        SysQuote quote = quoteService.selectQuoteById(id);
        if (quote == null)
        {
            return AppApiResponse.fail(404, "报价不存在");
        }

        // 查询询盘以确定买卖双方
        SysInquiry inquiry = inquiryService.selectInquiryById(quote.getInquiryId());
        if (inquiry == null)
        {
            return AppApiResponse.fail(404, "关联询盘不存在");
        }

        String operatorRole;
        if ("pending".equals(quote.getStatus()) && currentUserId.equals(inquiry.getBuyerId()))
        {
            // 卖方报价(status=pending)，买方拒绝
            operatorRole = "buyer";
        }
        else if ("pending_seller".equals(quote.getStatus()) && currentUserId.equals(inquiry.getSellerId()))
        {
            // 买方议价(status=pending_seller)，卖方拒绝
            operatorRole = "seller";
        }
        else
        {
            return AppApiResponse.fail(403, "无权操作该报价");
        }

        quoteService.rejectQuote(id, operatorRole);

        // 向关联的 TRADE 会话发送系统消息
        String amountYuan = formatYuan(quote.getPrice());
        String roleLabel = "buyer".equals(operatorRole) ? "买方" : "卖方";
        chatService.sendNoticeByInquiryId(quote.getInquiryId(),
                "[拒绝] " + roleLabel + "已拒绝报价 " + amountYuan);

        return AppApiResponse.ok();
    }

    /**
     * 从 AppIdentityContext 获取当前登录用户ID。
     */
    private Long currentUserId()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AppIdentityContext identity)
        {
            return identity.getUserId();
        }
        throw new RuntimeException("未登录或身份上下文异常");
    }

    /** 分转元格式化（price 字段为 BigDecimal，单位分） */
    private String formatYuan(BigDecimal fen)
    {
        if (fen == null) return "¥0.00";
        return "¥" + fen.divide(new BigDecimal("100")).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** 授权类型中文标签 */
    private String licenseLabel(String licenseType)
    {
        if (licenseType == null) return "未指定";
        return switch (licenseType)
        {
            case "exclusive"     -> "独家授权";
            case "non_exclusive" -> "非独家授权";
            case "adaptation"    -> "改编授权";
            default              -> licenseType;
        };
    }
}
