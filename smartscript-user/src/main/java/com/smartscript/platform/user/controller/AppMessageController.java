package com.smartscript.platform.user.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.constant.AppPageConstants;
import com.smartscript.platform.user.dto.AppApiResponse;
import com.smartscript.platform.user.dto.PageResult;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.security.AppIdentityContext;
import com.smartscript.platform.user.service.UserMessageService;

/**
 * A5 消息中心（契约 §1.5，规格 §8.6）。
 *
 * 路由顺序：静态段 {@code /unread-count} 与 {@code /read-all} 必须先于
 * {@code /{id}} 声明，否则会被路径变量吞掉（Spring 的精确匹配优先，
 * 显式声明顺序更直观，也便于后续加同级静态子路径）。
 *
 * 鉴权由 App 凭证域的 Spring Security 链完成；业务层以身份上下文 userId 为准。
 */
@RestController
@RequestMapping("/api/v1/messages")
public class AppMessageController
{
    private final UserMessageService messageService;

    public AppMessageController(UserMessageService messageService)
    {
        this.messageService = messageService;
    }

    @GetMapping
    public AppApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam Map<String, Object> params)
    {
        int pageNum = pageNum(params.get("pageNum"));
        int pageSize = pageSize(params.get("pageSize"));
        String type = str(params.get("type"));
        return AppApiResponse.ok(messageService.page(currentUserId(), type, pageNum, pageSize));
    }

    @GetMapping("/unread-count")
    public AppApiResponse<Map<String, Object>> unreadCount()
    {
        return AppApiResponse.ok(messageService.unreadCount(currentUserId()));
    }

    @PutMapping("/read-all")
    public AppApiResponse<Map<String, Object>> readAll()
    {
        return AppApiResponse.ok(messageService.markAllRead(currentUserId()));
    }

    @GetMapping("/{messageId}")
    public AppApiResponse<Map<String, Object>> detail(@PathVariable Long messageId)
    {
        return AppApiResponse.ok(messageService.detail(currentUserId(), messageId));
    }

    @PutMapping("/{messageId}/read")
    public AppApiResponse<Map<String, Object>> markRead(@PathVariable Long messageId)
    {
        return AppApiResponse.ok(messageService.markRead(currentUserId(), messageId));
    }

    private AppIdentityContext currentIdentity()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AppIdentityContext identity)
        {
            return identity;
        }
        throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "unauthorized");
    }

    private Long currentUserId()
    {
        return currentIdentity().getUserId();
    }

    private static String str(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 分页参数收敛到合法区间，越界收敛而不抛错（与 A4 PC 侧同一口径）。
     * 页码无上限（翻到哪页由数据决定），每页条数上限 100。
     */
    private static int pageNum(Object value)
    {
        int parsed = parseInt(value, AppPageConstants.DEFAULT_PAGE_NUM);
        return parsed < 1 ? AppPageConstants.DEFAULT_PAGE_NUM : parsed;
    }

    private static int pageSize(Object value)
    {
        int parsed = parseInt(value, AppPageConstants.DEFAULT_PAGE_SIZE);
        if (parsed < 1)
        {
            return AppPageConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(parsed, AppPageConstants.PAGE_SIZE_MAX);
    }

    private static int parseInt(Object value, int fallback)
    {
        if (value instanceof Number n)
        {
            return n.intValue();
        }
        if (value == null)
        {
            return fallback;
        }
        try
        {
            return Integer.parseInt(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }
}
