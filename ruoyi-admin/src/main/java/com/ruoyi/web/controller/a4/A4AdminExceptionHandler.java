package com.ruoyi.web.controller.a4;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.common.core.domain.AjaxResult;
import com.smartscript.platform.user.constant.AppAdminErrorCodes;
import com.smartscript.platform.user.exception.AppAdminException;

/**
 * A4 PC 管理接口错误语义（契约 §6）。
 *
 * 只作用于 A4AdminController，不改变若依原生控制器的异常行为。
 *
 * 关于 HTTP 状态：本控制器为 A4 自定义域，显式返回与 code 同值的真实 HTTP 状态，
 * 便于联调矩阵按 HTTP 断言。若依原生接口仍沿用其「HTTP 200 + body.code」约定，
 * 两者互不影响。
 *
 * 安全要求：错误响应不得暴露 SQL、内部类名、堆栈、文件路径、材料地址
 * 或目标账号是否存在的敏感细节，故对外只返回安全文案。
 */
@RestControllerAdvice(assignableTypes = { A4AdminController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class A4AdminExceptionHandler
{
    @ExceptionHandler(AppAdminException.class)
    public ResponseEntity<AjaxResult> handleAdmin(AppAdminException e)
    {
        AjaxResult body = AjaxResult.error(e.getCode(), safeMessage(e));
        return ResponseEntity.status(HttpStatus.valueOf(e.getHttpStatus())).body(body);
    }

    /**
     * 权限不足（@PreAuthorize 拒绝）必须返回 403。
     *
     * 必须显式声明：AccessDeniedException 继承 RuntimeException，
     * 若不单独处理会被下方的兜底 handler 捕获并误报为 500，
     * 使「无权限账号可通过直接接口完成敏感操作」的判定失去可信信号。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AjaxResult> handleAccessDenied(AccessDeniedException e)
    {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(AjaxResult.error(AppAdminErrorCodes.FORBIDDEN, "权限不足"));
    }

    /**
     * 参数绑定与类型不匹配统一为 400。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AjaxResult> handleIllegalArgument(IllegalArgumentException e)
    {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AjaxResult.error(AppAdminErrorCodes.BAD_REQUEST, AppAdminErrorCodes.INVALID_PARAM));
    }

    /**
     * 未预期异常统一为 500 安全文案。
     * 不使用 e.getMessage()，避免把内部细节透出到响应体。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AjaxResult> handleRuntime(RuntimeException e)
    {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AjaxResult.error(AppAdminErrorCodes.SYSTEM_ERROR, AppAdminErrorCodes.SYSTEM_ERROR_TEXT));
    }

    private String safeMessage(AppAdminException e)
    {
        String message = e.getMessage();
        if (message == null || message.isBlank())
        {
            return AppAdminErrorCodes.INVALID_PARAM;
        }
        // 兜底：出现明显内部泄漏特征时替换为通用文案
        String lower = message.toLowerCase();
        if (lower.contains("select ") || lower.contains("insert ") || lower.contains("update ")
                || lower.contains("sqlexception") || lower.contains("com.ruoyi")
                || lower.contains("com.smartscript") || lower.contains("java.")
                || message.contains("/") || message.contains("\\"))
        {
            return AppAdminErrorCodes.SYSTEM_ERROR_TEXT;
        }
        return message;
    }
}
