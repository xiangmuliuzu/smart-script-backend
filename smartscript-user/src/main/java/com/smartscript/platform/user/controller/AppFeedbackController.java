package com.smartscript.platform.user.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.constant.AppPageConstants;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.user.dto.FeedbackCreateRequest;
import com.smartscript.platform.user.dto.PageResult;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.security.AppIdentityContext;
import com.smartscript.platform.user.service.UserFeedbackService;
import jakarta.validation.Valid;

/**
 * A5 意见反馈（契约 §1.6，规格 §8.7）。
 *
 * 用户只访问自己的反馈：归属校验在服务层 SQL 完成，
 * 他人反馈与不存在的反馈返回同样的 404，不区分原因。
 *
 * PC 管理端的反馈处理接口在 A4AdminController，两者互不影响。
 */
@RestController
@RequestMapping("/api/v1/feedback")
public class AppFeedbackController
{
    private final UserFeedbackService feedbackService;

    public AppFeedbackController(UserFeedbackService feedbackService)
    {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public AppApiResponse<Map<String, Object>> create(@Valid @RequestBody FeedbackCreateRequest request)
    {
        return AppApiResponse.ok(feedbackService.create(currentUserId(), request));
    }

    @GetMapping
    public AppApiResponse<PageResult<Map<String, Object>>> list(@RequestParam Map<String, Object> params)
    {
        int pageNum = pageNum(params.get("pageNum"));
        int pageSize = pageSize(params.get("pageSize"));
        String status = params.get("status") == null ? null : String.valueOf(params.get("status"));
        return AppApiResponse.ok(feedbackService.page(currentUserId(), status, pageNum, pageSize));
    }

    @GetMapping("/{feedbackId}")
    public AppApiResponse<Map<String, Object>> detail(@PathVariable Long feedbackId)
    {
        return AppApiResponse.ok(feedbackService.detail(currentUserId(), feedbackId));
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
