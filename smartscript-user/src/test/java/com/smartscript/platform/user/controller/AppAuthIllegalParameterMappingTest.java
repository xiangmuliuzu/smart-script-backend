package com.smartscript.platform.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;

/**
 * H-04 §5.3 / §10：App 域非法参数必须映射为 400/40000，而不是被 RuntimeException 兜底成 500。
 *
 * 背景（第 6 批负向矩阵 H6-NEG-01）：`GET /messages/abc`、`GET /feedback/abc`、
 * `POST /users/me/avatar`（缺 file）曾分别返回 500/50000。根因是
 * 类型不匹配与一般 MultipartException 为运行时异常；缺失部件异常继承 ServletException。
 * 两类输入在修复前均落入 500 错误信封。
 *
 * 本测试在无 Spring 上下文的前提下直接调用 advice 方法，锁定「非法参数 → 400」且
 * 「未预期异常仍为 500」两条口径，防止映射被无意放宽或收紧。
 */
class AppAuthIllegalParameterMappingTest
{
    private final AppAuthExceptionHandler handler = new AppAuthExceptionHandler();

    @Test
    void typeMismatchMapsTo400()
    {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "messageId", null, new IllegalStateException("type mismatch"));

        ResponseEntity<AppApiResponse<Object>> resp = handler.handleIllegalParameter(e);

        assertEquals(400, resp.getStatusCode().value(), "路径变量类型不匹配必须为 400");
        assertNotNull(resp.getBody());
        assertEquals(AppAuthErrorCodes.PARAM, resp.getBody().getCode(), "非法参数业务码必须是 40000");
    }

    @Test
    void missingMultipartPartMapsTo400()
    {
        ResponseEntity<AppApiResponse<Object>> resp =
                handler.handleIllegalParameter(new MissingServletRequestPartException("file"));

        assertEquals(400, resp.getStatusCode().value(), "缺少 multipart 部件必须为 400");
        assertEquals(AppAuthErrorCodes.PARAM, resp.getBody().getCode());
    }

    @Test
    void multipartExceptionMapsTo400()
    {
        ResponseEntity<AppApiResponse<Object>> resp =
                handler.handleIllegalParameter(new MultipartException("not a multipart request"));

        assertEquals(400, resp.getStatusCode().value(), "multipart 请求格式错误必须为 400");
        assertEquals(AppAuthErrorCodes.PARAM, resp.getBody().getCode());
    }

    @Test
    void unexpectedRuntimeErrorStillMapsTo500()
    {
        ResponseEntity<AppApiResponse<Object>> resp = handler.handleRuntime(new IllegalStateException("boom"));

        assertEquals(500, resp.getStatusCode().value(), "未预期异常必须仍为 500，不得被非法参数映射吞掉");
        assertEquals(AppAuthErrorCodes.SYSTEM_ERROR, resp.getBody().getCode());
    }
}
