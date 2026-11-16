package com.smartscript.platform.trade.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.trade.service.TradeCopyrightService;
import com.smartscript.platform.trade.service.TradeCopyrightService.CopyrightForbiddenException;
import com.smartscript.platform.trade.service.TradeCopyrightService.CopyrightNotFoundException;
import com.smartscript.platform.user.security.AppIdentityContext;

/**
 * 买方版权材料只读接口（企业端「版权」栏与 App 共用）。
 * 路径：/api/v1/trade/copyright/*
 * 契约：docs/contracts/2026-11-02-enterprise-buyer-copyright.md
 */
@RestController
@RequestMapping("/api/v1/trade/copyright")
public class AppTradeCopyrightController
{
    private final TradeCopyrightService copyrightService;

    public AppTradeCopyrightController(TradeCopyrightService copyrightService)
    {
        this.copyrightService = copyrightService;
    }

    @GetMapping("/list")
    public AppApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        return AppApiResponse.ok(copyrightService.listForBuyer(currentUserId(), keyword, pageNum, pageSize));
    }

    @GetMapping("/{fileId}")
    public AppApiResponse<Map<String, Object>> detail(@PathVariable Long fileId)
    {
        try
        {
            return AppApiResponse.ok(copyrightService.detailForBuyer(currentUserId(), fileId));
        }
        catch (CopyrightNotFoundException e)
        {
            return AppApiResponse.fail(404, e.getMessage());
        }
        catch (CopyrightForbiddenException e)
        {
            return AppApiResponse.fail(403, e.getMessage());
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
