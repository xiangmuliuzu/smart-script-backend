package com.smartscript.platform.user.controller;

import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.user.exception.AppAuthException;

/**
 * A3/A5 App 凭证域错误信封。只作用于 App 端控制器，
 * 若依 PC 控制器与 A4 管理接口的异常语义不受影响。
 *
 * A5 起把用户中心（资料/实名/换绑/通知偏好/消息/反馈）的控制器一并纳入，
 * 使 App 用户域对外只有一套错误语义（契约 A5-USER-CENTER-CONTRACT-v1 §1.7）。
 */
@RestControllerAdvice(assignableTypes = { AppAuthController.class, AppUserController.class,
        AppMessageController.class, AppFeedbackController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppAuthExceptionHandler
{
    @ExceptionHandler(AppAuthException.class)
    public ResponseEntity<AppApiResponse<Object>> handleAuth(AppAuthException e)
    {
        AppApiResponse<Object> body = AppApiResponse.fail(e.getCode(), safeMessage(e.getCode(), e.getMessage()), e.getData());
        return ResponseEntity.status(e.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AppApiResponse<Object>> handleValidation(MethodArgumentNotValidException e)
    {
        var fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AppApiResponse.fail(AppAuthErrorCodes.PARAM, "invalid request",
                        java.util.Map.of("fieldErrors", fieldErrors)));
    }

    /**
     * Unexpected errors from App auth controllers only. Does not intercept PC controllers.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AppApiResponse<Object>> handleRuntime(RuntimeException e)
    {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AppApiResponse.fail(AppAuthErrorCodes.SYSTEM_ERROR, "internal error"));
    }

    /**
     * 对外文案裁决：
     *   1. 契约已定义对外文案的码（A5 用户中心新增码）直接使用该文案；
     *   2. A3 码沿用既有英文文案，保持 A3 契约与既有测试口径不变；
     *   3. 其余情况使用异常自带文案，但先做泄漏过滤（SQL 片段、内部类名、路径）。
     *
     * 过滤而非一律替换的原因：用户中心的服务层文案是面向用户的中文提示
     * （如「当前实名状态不允许该操作」），一律替换会让 App 只能显示通用错误；
     * 而过滤能同时挡住内部细节泄漏。
     */
    static String safeMessage(int code, String fallback)
    {
        return switch (code)
        {
            case AppUserErrorCodes.RESOURCE_NOT_FOUND -> AppUserErrorCodes.RESOURCE_NOT_FOUND_TEXT;
            case AppUserErrorCodes.REAL_NAME_CONFLICT -> AppUserErrorCodes.REAL_NAME_CONFLICT_TEXT;
            case AppUserErrorCodes.DUPLICATE_SUBMIT -> AppUserErrorCodes.DUPLICATE_SUBMIT_TEXT;
            case AppAuthErrorCodes.PARAM -> "invalid request";
            case AppAuthErrorCodes.SMS_CODE_INVALID -> "sms code invalid";
            case AppAuthErrorCodes.SMS_CODE_EXPIRED -> "sms code expired";
            case AppAuthErrorCodes.SMS_CODE_USED -> "sms code already used";
            case AppAuthErrorCodes.PHONE_TAKEN -> "phone already registered";
            case AppAuthErrorCodes.AGREEMENT_MISMATCH -> "agreement version mismatch";
            case AppAuthErrorCodes.PASSWORD_STATE_CONFLICT -> "password state conflict";
            case AppAuthErrorCodes.UNAUTHORIZED -> "unauthorized";
            case AppAuthErrorCodes.ACCESS_EXPIRED -> "access token expired";
            case AppAuthErrorCodes.REFRESH_INVALID -> "refresh token invalid";
            case AppAuthErrorCodes.REFRESH_REPLAY -> "refresh token replay";
            case AppAuthErrorCodes.DOMAIN_OR_PERMISSION -> "forbidden";
            case AppAuthErrorCodes.ACCOUNT_DISABLED -> "account disabled";
            case AppAuthErrorCodes.SMS_COOLDOWN -> "sms cooldown";
            case AppAuthErrorCodes.SMS_RATE_LIMIT -> "rate limited";
            case AppAuthErrorCodes.OAUTH_NOT_OPEN -> "oauth provider not open";
            default -> leakFree(fallback);
        };
    }

    /** 泄漏过滤：出现内部特征时退回通用文案，避免把实现细节透给客户端。 */
    private static String leakFree(String message)
    {
        if (message == null || message.isBlank())
        {
            return "error";
        }
        String lower = message.toLowerCase();
        if (lower.contains("select ") || lower.contains("insert ") || lower.contains("update ")
                || lower.contains("sqlexception") || lower.contains("com.ruoyi")
                || lower.contains("com.smartscript") || lower.contains("java.")
                || message.contains("/") || message.contains("\\"))
        {
            return "error";
        }
        return message;
    }
}
