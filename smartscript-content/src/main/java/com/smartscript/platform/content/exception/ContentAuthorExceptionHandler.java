package com.smartscript.platform.content.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.controller.app.AppWorkManageController;
import com.smartscript.platform.content.controller.app.AppUserWorkController;
import com.smartscript.platform.content.controller.app.AppChapterWriteController;

/** 创作准入失败沿用 App 信封及 HTTP 状态，不返回 PC 管理端的授权文案。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(assignableTypes = { AppWorkManageController.class, AppUserWorkController.class,
        AppChapterWriteController.class })
public class ContentAuthorExceptionHandler
{
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AppApiResponse<Object>> denied(AccessDeniedException exception)
    {
        return ResponseEntity.status(403).body(AppApiResponse.fail(403, "请先申请开通创作者能力"));
    }
}
