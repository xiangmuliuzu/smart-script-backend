package com.ruoyi.web.controller.a4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.smartscript.platform.user.constant.AppAdminErrorCodes;

/**
 * H-04 §5.3 / §10：A4 PC 管理域非法参数必须映射为 400（code 与 HTTP 状态双写），
 * 而不是被 RuntimeException 兜底成 500。
 *
 * 背景（第 6 批负向矩阵 H6-NEG-01）：`GET /api/v1/admin/app-users/abc` 曾返回 500/500。
 * 根因是 {@code MethodArgumentTypeMismatchException} 继承 RuntimeException，被
 * {@link A4AdminExceptionHandler#handleRuntime} 收敛。
 */
class A4AdminIllegalParameterMappingTest
{
    private final A4AdminExceptionHandler handler = new A4AdminExceptionHandler();

    @Test
    void typeMismatchMapsTo400()
    {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "userId", null, new IllegalStateException("type mismatch"));

        ResponseEntity<AjaxResult> resp = handler.handleIllegalParameter(e);

        assertEquals(400, resp.getStatusCode().value(), "路径变量类型不匹配必须为 400");
        assertNotNull(resp.getBody());
        assertEquals(AppAdminErrorCodes.BAD_REQUEST, resp.getBody().get("code"), "业务码与 HTTP 状态双写为 400");
    }

    @Test
    void missingMultipartPartMapsTo400()
    {
        ResponseEntity<AjaxResult> resp =
                handler.handleIllegalParameter(new MissingServletRequestPartException("file"));

        assertEquals(400, resp.getStatusCode().value());
        assertEquals(AppAdminErrorCodes.BAD_REQUEST, resp.getBody().get("code"));
    }

    @Test
    void unexpectedRuntimeErrorStillMapsTo500()
    {
        ResponseEntity<AjaxResult> resp = handler.handleRuntime(new IllegalStateException("boom"));

        assertEquals(500, resp.getStatusCode().value(), "未预期异常必须仍为 500");
        assertEquals(AppAdminErrorCodes.SYSTEM_ERROR, resp.getBody().get("code"));
    }
}
