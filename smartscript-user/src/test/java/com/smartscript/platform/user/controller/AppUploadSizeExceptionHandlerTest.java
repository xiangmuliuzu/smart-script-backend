package com.smartscript.platform.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.user.constant.AppUserErrorCodes;

/**
 * H7-ERR-01：容器级上传超限必须返回 413，且按域返回正确信封、不泄露原始异常文案。
 *
 * 背景（第 8 批实测）：11MB 头像上传在 HandlerMapping 之前由容器抛出
 * {@code MaxUploadSizeExceededException}，曾落到若依全局处理器返回
 * 「HTTP 200 + code 500 + Maximum upload size exceeded」。
 */
class AppUploadSizeExceptionHandlerTest
{
    private final AppUploadSizeExceptionHandler handler = new AppUploadSizeExceptionHandler();

    private static MaxUploadSizeExceededException oversize()
    {
        return new MaxUploadSizeExceededException(10 * 1024 * 1024L);
    }

    @Test
    void appPathReturns413WithAppEnvelope()
    {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/users/me/avatar");

        ResponseEntity<Object> resp = handler.handleMaxUploadSizeExceeded(oversize(), req);

        assertEquals(413, resp.getStatusCode().value(), "App 上传超限必须为 413");
        Object body = assertInstanceOf(AppApiResponse.class, resp.getBody());
        assertEquals(AppUserErrorCodes.PAYLOAD_TOO_LARGE, ((AppApiResponse<?>) body).getCode());
        assertEquals(AppUserErrorCodes.PAYLOAD_TOO_LARGE_TEXT, ((AppApiResponse<?>) body).getMessage());
    }

    /**
     * PC 管理端同样在 {@code /api/v1/admin} 下，必须用显式白名单而非 {@code /api/v1/} 前缀粗判，
     * 否则会把 PC 超限误判为 App 域、返回错误信封（第 8 批复核实测到该缺陷）。
     */
    @Test
    void pcAdminPathReturns413WithAjaxResultNotAppEnvelope()
    {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/admin/notifications");

        ResponseEntity<Object> resp = handler.handleMaxUploadSizeExceeded(oversize(), req);

        assertEquals(413, resp.getStatusCode().value());
        Object body = assertInstanceOf(AjaxResult.class, resp.getBody(),
                "PC 管理路径不得使用 App 信封");
        assertEquals(AppUserErrorCodes.PAYLOAD_TOO_LARGE, ((AjaxResult) body).get("code"));
        assertEquals(AppUserErrorCodes.PAYLOAD_TOO_LARGE_TEXT, ((AjaxResult) body).get("msg"));
    }

    /**
     * App 域必须同时覆盖**根路径**（无尾斜杠，如 {@code POST /api/v1/feedback}、{@code GET /api/v1/messages}）
     * 与子路径。第 8 批复核实测：白名单曾写作带尾斜杠形式，导致根路径落到 PC 信封。
     */
    @Test
    void appDomainRootAndSubPathsUseAppEnvelope()
    {
        for (String path : new String[] {
                "/api/v1/feedback", "/api/v1/messages", "/api/v1/auth/x", "/api/v1/users/me/avatar",
                "/api/v1/messages/unread-count", "/api/v1/feedback/123" })
        {
            Object body = handler.handleMaxUploadSizeExceeded(oversize(), new MockHttpServletRequest("POST", path)).getBody();
            assertInstanceOf(AppApiResponse.class, body, path + " 应为 App 信封（根路径与子路径都覆盖）");
        }
    }

    /** 仅前缀相似但不在同一路径段（{@code /api/v1/feedbackx}）或 PC/框架路径不得误判为 App 域。 */
    @Test
    void lookAlikePrefixAndNonAppPathsUseAjaxResult()
    {
        for (String path : new String[] {
                "/api/v1/feedbackx", "/api/v1/messagesx", "/api/v1/admin/notifications", "/common/upload", "/api/v1" })
        {
            Object body = handler.handleMaxUploadSizeExceeded(oversize(), new MockHttpServletRequest("POST", path)).getBody();
            assertInstanceOf(AjaxResult.class, body, path + " 不得误判为 App 域");
        }
    }

    @Test
    void nonAppPathReturns413WithAjaxResultAndNoLeak()
    {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/common/upload");

        ResponseEntity<Object> resp = handler.handleMaxUploadSizeExceeded(oversize(), req);

        assertEquals(413, resp.getStatusCode().value(), "非 App 路径上传超限也必须为 413");
        Object body = assertInstanceOf(AjaxResult.class, resp.getBody());
        assertEquals(AppUserErrorCodes.PAYLOAD_TOO_LARGE, ((AjaxResult) body).get("code"));
        assertEquals(AppUserErrorCodes.PAYLOAD_TOO_LARGE_TEXT, ((AjaxResult) body).get("msg"),
                "不得把容器异常原文（Maximum upload size exceeded）透给客户端");
    }

    @Test
    void nullRequestDoesNotThrow()
    {
        ResponseEntity<Object> resp = handler.handleMaxUploadSizeExceeded(oversize(), null);
        assertEquals(413, resp.getStatusCode().value());
    }
}
