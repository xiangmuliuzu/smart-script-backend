package com.smartscript.platform.user.controller;

import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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
        AppMessageController.class, AppFeedbackController.class, AppAnnouncementController.class })
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
                        fe -> fe.getDefaultMessage() == null ? "填写不正确" : fe.getDefaultMessage(),
                        (a, b) -> a));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AppApiResponse.fail(AppAuthErrorCodes.PARAM, "您的信息不符合规范，请检查后重试",
                        java.util.Map.of("fieldErrors", fieldErrors)));
    }

    /**
     * 非法参数类异常统一为 400/40000（§5.3 / §10）：
     *   - 路径变量或查询参数类型不匹配（如 `/messages/{id}` 传非数字）；
     *   - multipart 请求缺少必需部件（如头像上传缺 `file`）；
     *   - 缺少必需请求参数 / 请求体不可解析。
     *
     * 类型不匹配和 MultipartException 属于运行时异常；缺失 multipart 部件继承 ServletException。
     * 显式映射可避免这些非法输入落入 500 错误信封。
     */
    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class, MultipartException.class,
            HttpMessageNotReadableException.class })
    public ResponseEntity<AppApiResponse<Object>> handleIllegalParameter(Exception e)
    {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AppApiResponse.fail(AppAuthErrorCodes.PARAM, "您的信息不符合规范，请检查后重试"));
    }

    /**
     * Unexpected errors from App auth controllers only. Does not intercept PC controllers.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AppApiResponse<Object>> handleRuntime(RuntimeException e)
    {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AppApiResponse.fail(AppAuthErrorCodes.SYSTEM_ERROR, "服务暂时不可用，请稍后再试"));
    }

    /**
     * 对外文案裁决：
     *   1. 有固定语义的错误码使用统一中文文案；
     *   2. 参数类错误允许服务层携带安全中文提示（如实名姓名/证件号），
     *      英文或技术内容一律回退通用文案；
     *   3. 错误码与 HTTP 状态保持不变，文案不再沿用 A3 英文。
     */
    static String safeMessage(int code, String fallback)
    {
        return switch (code)
        {
            case AppUserErrorCodes.RESOURCE_NOT_FOUND -> "内容不存在或暂时不可用";
            case AppUserErrorCodes.REAL_NAME_CONFLICT -> "实名认证正在审核或已通过，请勿重复提交";
            case AppUserErrorCodes.DUPLICATE_SUBMIT -> "请勿重复提交";
            case AppUserErrorCodes.PAYLOAD_TOO_LARGE -> "文件过大，请更换后重试";
            case AppAuthErrorCodes.PARAM -> safeUserMessage(fallback, "您的信息不符合规范，请检查后重试");
            case AppAuthErrorCodes.SMS_CODE_INVALID -> "验证码错误，请重新输入";
            case AppAuthErrorCodes.SMS_CODE_EXPIRED -> "验证码已过期，请重新获取";
            case AppAuthErrorCodes.SMS_CODE_USED -> "验证码已使用，请重新获取";
            case AppAuthErrorCodes.PHONE_TAKEN -> "该手机号或邮箱已注册，请直接登录";
            case AppAuthErrorCodes.AGREEMENT_MISMATCH -> "协议已更新，请重新阅读并同意";
            case AppAuthErrorCodes.PASSWORD_STATE_CONFLICT -> "当前密码状态无法执行此操作，请刷新后重试";
            case AppAuthErrorCodes.UNAUTHORIZED -> "登录状态已过期，请重新登录";
            case AppAuthErrorCodes.ACCESS_EXPIRED -> "登录状态已过期，请重新登录";
            case AppAuthErrorCodes.REFRESH_INVALID -> "登录状态已过期，请重新登录";
            case AppAuthErrorCodes.DOMAIN_OR_PERMISSION -> "暂无权限执行此操作";
            case AppAuthErrorCodes.ACCOUNT_DISABLED -> "账号暂时无法使用，请联系客服或管理员";
            case AppAuthErrorCodes.SMS_COOLDOWN -> "操作过于频繁，请稍后再试";
            case AppAuthErrorCodes.SMS_RATE_LIMIT -> "操作过于频繁，请稍后再试";
            case AppAuthErrorCodes.SYSTEM_ERROR -> "服务暂时不可用，请稍后再试";
            default -> safeUserMessage(fallback, "服务暂时不可用，请稍后再试");
        };
    }

    /** 服务层文案只在“含中文且不含技术特征”时透传，否则使用兜底。 */
    static String safeUserMessage(String message, String fallback)
    {
        if (message == null || message.isBlank() || !containsChinese(message))
        {
            return fallback;
        }
        String lower = message.toLowerCase();
        if (lower.contains("select ") || lower.contains("insert ") || lower.contains("update ")
                || lower.contains("sqlexception") || lower.contains("exception")
                || lower.contains("com.ruoyi") || lower.contains("com.smartscript")
                || lower.contains("java.") || lower.contains("http")
                || lower.contains("null") || lower.contains("undefined")
                || message.contains("/") || message.contains("\\"))
        {
            return fallback;
        }
        return message;
    }

    private static boolean containsChinese(String message)
    {
        return message.codePoints().anyMatch(cp ->
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }
}
