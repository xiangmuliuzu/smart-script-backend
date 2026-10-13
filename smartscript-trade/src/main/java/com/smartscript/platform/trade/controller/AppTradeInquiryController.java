package com.smartscript.platform.trade.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.service.TradeInquiryService;

/**
 * APP端询盘接口（C交易域）。
 *
 * 复用 smartscript-trade 模块的 TradeInquiryService，使用 APP 域的鉴权。
 * 路径：/api/v1/trade/inquiry/*
 *
 * 鉴权：由 APP 凭证域的 Spring Security 链完成；业务层以
 * SecurityContextHolder 中的 Authentication.getPrincipal()（Long userId）为准。
 */
@RestController
@RequestMapping("/api/v1/trade/inquiry")
public class AppTradeInquiryController
{
    private final TradeInquiryService inquiryService;

    public AppTradeInquiryController(TradeInquiryService inquiryService)
    {
        this.inquiryService = inquiryService;
    }

    /**
     * 询盘列表（分页）。
     *
     * @param role     buyer（我发起的）/ seller（我收到的）/ null（全部）
     * @param status   状态筛选
     * @param keyword  关键词
     * @param pageNum  页码
     * @param pageSize 每页数量
     */
    @GetMapping("/list")
    public AppApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        Long currentUserId = currentUserId();

        // 构建查询条件
        SysInquiry query = new SysInquiry();
        if ("buyer".equals(role))
        {
            query.setBuyerId(currentUserId);
        }
        else if ("seller".equals(role))
        {
            query.setSellerId(currentUserId);
        }
        if (status != null && !status.isEmpty())
        {
            query.setStatus(status);
        }
        if (keyword != null && !keyword.isEmpty())
        {
            query.setKeyword(keyword);
        }

        // 调用 service 查询
        List<SysInquiry> list = inquiryService.selectInquiryList(query);

        // 手动分页
        int total = list.size();
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<SysInquiry> pageList = (fromIndex < total) ? list.subList(fromIndex, toIndex) : List.of();

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("list", pageList);
        return AppApiResponse.ok(result);
    }

    /**
     * 询盘详情。
     */
    @GetMapping("/{id}")
    public AppApiResponse<SysInquiry> detail(@PathVariable Long id)
    {
        SysInquiry inquiry = inquiryService.selectInquiryById(id);
        if (inquiry == null)
        {
            return AppApiResponse.fail(404, "询盘不存在");
        }

        // 权限校验：只能查看自己的询盘
        Long currentUserId = currentUserId();
        if (!currentUserId.equals(inquiry.getBuyerId()) && !currentUserId.equals(inquiry.getSellerId()))
        {
            return AppApiResponse.fail(403, "无权查看此询盘");
        }

        return AppApiResponse.ok(inquiry);
    }

    /**
     * 接受询盘。
     */
    @PutMapping("/{id}/accept")
    public AppApiResponse<Void> accept(@PathVariable Long id)
    {
        SysInquiry inquiry = inquiryService.selectInquiryById(id);
        if (inquiry == null)
        {
            return AppApiResponse.fail(404, "询盘不存在");
        }

        Long currentUserId = currentUserId();
        if (!currentUserId.equals(inquiry.getSellerId()))
        {
            return AppApiResponse.fail(403, "只有卖方可以接受询盘");
        }

        inquiryService.acceptInquiry(id);
        return AppApiResponse.ok();
    }

    /**
     * 拒绝询盘。
     */
    @PutMapping("/{id}/reject")
    public AppApiResponse<Void> reject(@PathVariable Long id)
    {
        SysInquiry inquiry = inquiryService.selectInquiryById(id);
        if (inquiry == null)
        {
            return AppApiResponse.fail(404, "询盘不存在");
        }

        Long currentUserId = currentUserId();
        if (!currentUserId.equals(inquiry.getSellerId()))
        {
            return AppApiResponse.fail(403, "只有卖方可以拒绝询盘");
        }

        inquiryService.rejectInquiry(id);
        return AppApiResponse.ok();
    }

    /**
     * 关闭询盘。
     */
    @PutMapping("/{id}/close")
    public AppApiResponse<Void> close(@PathVariable Long id)
    {
        SysInquiry inquiry = inquiryService.selectInquiryById(id);
        if (inquiry == null)
        {
            return AppApiResponse.fail(404, "询盘不存在");
        }

        Long currentUserId = currentUserId();
        if (!currentUserId.equals(inquiry.getBuyerId()))
        {
            return AppApiResponse.fail(403, "只有买方可以关闭询盘");
        }

        inquiryService.closeInquiry(id);
        return AppApiResponse.ok();
    }

    /**
     * 从 SecurityContext 获取当前登录用户ID。
     * APP 域鉴权后 Authentication.principal 为 Long userId。
     */
    private Long currentUserId()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null)
        {
            throw new RuntimeException("未登录");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long)
        {
            return (Long) principal;
        }
        // AppIdentityContext.getPrincipal() 返回 userId (Long)
        return Long.valueOf(principal.toString());
    }
}
