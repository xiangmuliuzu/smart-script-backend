package com.smartscript.platform.user.controller;

import java.util.List;
import java.util.Map;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.NoticeReceipt;
import com.ruoyi.system.service.NoticeRecipientService;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.dto.PageResult;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.security.AppIdentityContext;

/** PC 普通用户与 App 共用公告读取，身份完全来自 App 凭证域。 */
@RestController
@RequestMapping("/api/v1/announcements")
public class AppAnnouncementController
{
    private final NoticeRecipientService service;

    public AppAnnouncementController(NoticeRecipientService service) { this.service = service; }

    @GetMapping
    public AppApiResponse<PageResult<NoticeReceipt>> list(@RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        AppIdentityContext identity = identity();
        PageHelper.startPage(Math.max(1, pageNum), Math.max(1, Math.min(100, pageSize)));
        List<NoticeReceipt> list = service.list(identity.getUserId(), identity.getUserType());
        return AppApiResponse.ok(PageResult.of(new PageInfo<>(list).getTotal(), list));
    }

    @GetMapping("/{noticeId}")
    public AppApiResponse<NoticeReceipt> detail(@PathVariable Long noticeId)
    {
        AppIdentityContext identity = identity();
        return AppApiResponse.ok(service.detail(identity.getUserId(), identity.getUserType(), noticeId));
    }

    @GetMapping("/unread-count")
    public AppApiResponse<Map<String, Object>> unreadCount()
    {
        AppIdentityContext identity = identity();
        return AppApiResponse.ok(Map.of("total", service.unreadCount(identity.getUserId(), identity.getUserType())));
    }

    @PutMapping("/{noticeId}/read")
    public AppApiResponse<Map<String, Object>> read(@PathVariable Long noticeId)
    {
        AppIdentityContext identity = identity();
        return AppApiResponse.ok(Map.of("changed", service.markRead(identity.getUserId(), identity.getUserType(), noticeId)));
    }

    @PutMapping("/read-all")
    public AppApiResponse<Map<String, Object>> readAll()
    {
        AppIdentityContext identity = identity();
        return AppApiResponse.ok(Map.of("updated", service.markAllRead(identity.getUserId(), identity.getUserType())));
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<AppApiResponse<Object>> handleNoticeError(ServiceException error)
    {
        int status = error.getCode() != null && error.getCode() == 403 ? 403 : 404;
        return ResponseEntity.status(status).body(AppApiResponse.fail(
                status == 403 ? AppUserErrorCodes.DOMAIN_OR_PERMISSION : AppUserErrorCodes.RESOURCE_NOT_FOUND,
                status == 403 ? "无权阅读公告" : "公告不存在或已关闭"));
    }

    private AppIdentityContext identity()
    {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AppIdentityContext identity))
            throw new AppAuthException(AppUserErrorCodes.UNAUTHORIZED, 401, "unauthorized");
        if (!List.of("01", "02", "03").contains(identity.getUserType()))
            throw new AppAuthException(AppUserErrorCodes.DOMAIN_OR_PERMISSION, 403, "无权阅读公告");
        return identity;
    }
}
