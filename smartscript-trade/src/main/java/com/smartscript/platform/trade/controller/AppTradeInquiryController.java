package com.smartscript.platform.trade.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
import com.smartscript.platform.trade.domain.SysWork;
import com.smartscript.platform.trade.service.TradeInquiryService;
import com.smartscript.platform.trade.service.TradeWorkService;
import com.smartscript.platform.user.dto.ChatSessionCreateRequest;
import com.smartscript.platform.user.service.ChatService;

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
    private final TradeWorkService workService;
    private final ChatService chatService;

    public AppTradeInquiryController(TradeInquiryService inquiryService, TradeWorkService workService, ChatService chatService)
    {
        this.inquiryService = inquiryService;
        this.workService = workService;
        this.chatService = chatService;
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
        // 库存元→APP分
        list.forEach(i -> i.setBudget(yuanToFen(i.getBudget())));

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
     * 创建询盘。
     */
    @PostMapping
    public AppApiResponse<SysInquiry> create(@RequestBody SysInquiry inquiry)
    {
        Long currentUserId = currentUserId();
        inquiry.setBuyerId(currentUserId);
        inquiry.setStatus("pending");
        // APP端设置createBy，避免TradeInquiryService调用SecurityUtils.getUsername()
        inquiry.setCreateBy(String.valueOf(currentUserId));
        // APP传分→库存元（sys_inquiry.budget 与 PC 端一致以元存储）
        inquiry.setBudget(fenToYuan(inquiry.getBudget()));

        // 根据workId查询作品，获取作者ID作为sellerId
        if (inquiry.getWorkId() != null && inquiry.getSellerId() == null)
        {
            SysWork work = workService.selectWorkById(inquiry.getWorkId());
            if (work != null)
            {
                inquiry.setSellerId(work.getAuthorId());
            }
        }

        SysInquiry created = inquiryService.createInquiry(inquiry);
        created.setBudget(yuanToFen(created.getBudget()));
        return AppApiResponse.ok(created);
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

        inquiry.setBudget(yuanToFen(inquiry.getBudget()));
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

        inquiryService.acceptInquiry(id, String.valueOf(currentUserId));
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

        inquiryService.rejectInquiry(id, String.valueOf(currentUserId));
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

        inquiryService.closeInquiry(id, String.valueOf(currentUserId));
        return AppApiResponse.ok();
    }

    /**
     * 创建 TRADE 类型聊天会话（买卖双方沟通）。
     *
     * 根据询盘ID查找关联作品，确定卖方（作者）作为 user2，
     * 当前用户作为 user1，创建幂等会话。
     */
    @PostMapping("/chat/sessions")
    public AppApiResponse<Map<String, Object>> createTradeChatSession(@RequestParam Long inquiryId)
    {
        Long currentUserId = currentUserId();

        // 查找询盘
        SysInquiry inquiry = inquiryService.selectInquiryById(inquiryId);
        if (inquiry == null)
        {
            return AppApiResponse.fail(404, "询盘不存在");
        }

        // 权限校验：只有询盘参与方可以创建会话
        if (!currentUserId.equals(inquiry.getBuyerId()) && !currentUserId.equals(inquiry.getSellerId()))
        {
            return AppApiResponse.fail(403, "无权操作该询盘");
        }

        // 查找作品获取卖方ID和业务名称
        SysWork work = null;
        if (inquiry.getWorkId() != null)
        {
            work = workService.selectWorkById(inquiry.getWorkId());
        }

        // 确定 user1（买方）和 user2（卖方）
        Long buyerId = inquiry.getBuyerId();
        Long sellerId = inquiry.getSellerId();
        if (sellerId == null && work != null)
        {
            sellerId = work.getAuthorId();
        }
        if (sellerId == null)
        {
            return AppApiResponse.fail(400, "询盘未关联卖方");
        }

        // 构建会话创建请求：user1=当前用户，user2=对方
        ChatSessionCreateRequest req = new ChatSessionCreateRequest();
        req.setSessionType("TRADE");
        req.setInquiryId(inquiryId);
        req.setBusinessType("INQUIRY");
        req.setBusinessId(inquiryId);
        req.setBusinessName(work != null ? work.getTitle() : "询盘#" + inquiryId);

        if (currentUserId.equals(buyerId))
        {
            // 买方发起：user1=买方，user2=卖方
            req.setTargetAdminId(sellerId);
        }
        else
        {
            // 卖方发起：user1=卖方，user2=买方
            req.setTargetAdminId(buyerId);
        }

        Map<String, Object> result = chatService.createOrGetSession(currentUserId, req);
        return AppApiResponse.ok(result);
    }

    /** APP入参分→库存元（与 PC 端一致以元存储） */
    private BigDecimal fenToYuan(BigDecimal fen)
    {
        if (fen == null) return null;
        return fen.divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }

    /** 库存元→APP出参分 */
    private BigDecimal yuanToFen(BigDecimal yuan)
    {
        if (yuan == null) return null;
        return yuan.multiply(new BigDecimal("100"));
    }

    /**
     * 从 SecurityContext 获取当前登录用户ID。
     * APP 域鉴权后 Authentication.principal 为 Long userId。
     */
    private Long currentUserId()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
        {
            throw new RuntimeException("未登录：Authentication is null");
        }
        Object principal = auth.getPrincipal();
        if (principal == null)
        {
            throw new RuntimeException("未登录：Principal is null");
        }
        if (principal instanceof Long)
        {
            return (Long) principal;
        }
        // AppIdentityContext.getPrincipal() 返回 userId (Long)
        try
        {
            return Long.valueOf(principal.toString());
        }
        catch (Exception e)
        {
            throw new RuntimeException("获取用户账户异常: principal=" + principal.getClass().getName() + ", value=" + principal);
        }
    }
}
