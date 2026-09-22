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
import com.smartscript.platform.user.dto.AppApiResponse;
import com.smartscript.platform.user.exception.AppAuthException;

/**
 * A3 contract error envelope. Scoped to App auth controllers only so RuoYi PC
 * exception semantics remain untouched.
 */
@RestControllerAdvice(assignableTypes = { AppAuthController.class })
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

    static String safeMessage(int code, String fallback)
    {
        return switch (code)
        {
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
            default -> fallback == null || fallback.isBlank() ? "error" : fallback;
        };
    }
}
