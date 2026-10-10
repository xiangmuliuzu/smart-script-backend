package com.smartscript.platform.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.user.constant.AppUserErrorCodes;

/**
 * 上传内容超限（413）统一处理（第 8 批，H7-ERR-01）。
 *
 * 为什么不能放进 {@link AppAuthExceptionHandler}：容器级超限在
 * {@code DispatcherServlet.checkMultipart} 阶段抛出，早于 HandlerMapping，
 * 此时没有已解析的处理器方法，{@code assignableTypes} 限定的 advice 不适用，
 * 异常会落到若依全局处理器并返回「HTTP 200 + code 500 + 原始异常文案」。
 * 因此这里必须是**无类型限定**的 advice，但只处理一个异常类型，收敛面最小。
 *
 * 语义：
 *   - **A 模块 App 域端点**（见 {@link #APP_DOMAIN_PREFIXES}）返回 App 契约信封 {@code {code,message,data}}；
 *   - 其它路径（含 PC 管理端 {@code /api/v1/admin/**} 与若依原生 {@code /common/**}）返回若依 {@code AjaxResult}；
 *   - 两者 HTTP 状态均为 413，文案不暴露容器上限、文件名或异常细节。
 *
 * 信封判定必须用**显式白名单**而不是 {@code uri.startsWith("/api/v1/")}：
 * PC 管理端同样位于 {@code /api/v1/admin}，用前缀粗判会把它误判为 App 域
 * （第 8 批复核实测：11MB 打到 {@code /api/v1/admin/notifications} 曾返回 App 信封）。
 *
 * 注意：服务层对头像另有 5MB 预检（{@code AppFileUploadService}）。H-04 决策甲（2026-09-28
 * 负责人批准）已将该预检由 400/40000 统一为 413/41300 与同一安全文案，与本处理器口径一致。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppUploadSizeExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(AppUploadSizeExceptionHandler.class);

    /**
     * App 域的端点前缀白名单（App 契约信封）；PC 管理端 {@code /api/v1/admin} 不在其中。
     *
     * /api/v1/content 为 B 模块 App 域（2.9.1 内容上传），同样返回 App 信封。
     */
    private static final List<String> APP_DOMAIN_PREFIXES = List.of(
            "/api/v1/auth", "/api/v1/users", "/api/v1/messages", "/api/v1/feedback", "/api/v1/content", "/api/v1/announcements");

    /**
     * 是否为 App 域路径：必须同时覆盖**根路径本身**（如 {@code POST /api/v1/feedback}、
     * {@code GET /api/v1/messages}，无尾斜杠）与**子路径**（如 {@code /api/v1/users/me/avatar}）。
     *
     * 用「等于前缀」或「前缀 + 斜杠开头」判定，而不是单纯 {@code startsWith(prefix)}：
     * 后者会把 {@code /api/v1/feedbackx} 这类仅前缀相似的路径误判为 App 域。
     */
    private static boolean isAppDomain(String uri)
    {
        if (uri == null)
        {
            return false;
        }
        for (String prefix : APP_DOMAIN_PREFIXES)
        {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/"))
            {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e,
            HttpServletRequest request)
    {
        String uri = request == null ? null : request.getRequestURI();
        boolean appDomain = isAppDomain(uri);
        // 只记录 URI 与是否 App 域，不记录文件名或请求体
        log.warn("upload size exceeded uri={} appDomain={} maxUploadSize={}", uri, appDomain, e.getMaxUploadSize());

        if (appDomain)
        {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(AppApiResponse.fail(AppUserErrorCodes.PAYLOAD_TOO_LARGE,
                            AppUserErrorCodes.PAYLOAD_TOO_LARGE_TEXT));
        }
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(AjaxResult.error(AppUserErrorCodes.PAYLOAD_TOO_LARGE,
                        AppUserErrorCodes.PAYLOAD_TOO_LARGE_TEXT));
    }
}
