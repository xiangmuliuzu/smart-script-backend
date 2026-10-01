package com.ruoyi.framework.security.handle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 * 第 11 批复核 H11-REV-01：PC 401 错误响应不得回显路径里的短时令牌。
 *
 * 背景：`AuthenticationEntryPointImpl` 把请求 URI 拼进 401 提示文案。材料读取接口把令牌放在
 * 路径里（`/api/v1/admin/material/content/{token}`），未认证请求的响应因此回显完整令牌；
 * 操作日志已脱敏，响应却漏了。本测试锁定：令牌段替换为 `{redacted}`，正常路径文案不变，
 * 且既有「HTTP 200 + body.code 401」约定不得改变。
 */
class AuthenticationEntryPointImplTest
{
    private MockHttpServletResponse commence(String method, String uri) throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        // 直接 new：审计回调为 null（非 Spring 装配），只验证响应口径
        new AuthenticationEntryPointImpl().commence(request, response,
                new InsufficientAuthenticationException("anonymous"));
        return response;
    }

    @Test
    void tokenBearingUriIsRedactedAndStatusConventionKept() throws Exception
    {
        String token = "h11SynthMaterialTokenAbCdEf0123456789";
        MockHttpServletResponse response = commence("GET", "/api/v1/admin/material/content/" + token);
        String body = response.getContentAsString();

        assertFalse(body.contains(token), "401 响应不得回显路径令牌: " + body);
        assertTrue(body.contains("/api/v1/admin/material/content/{redacted}"), "令牌段必须脱敏: " + body);
        // 既有约定不变：HTTP 200 + body.code 401
        assertEquals(200, response.getStatus());
        assertTrue(body.contains("\"code\":401"), "响应码语义保持 401: " + body);
    }

    @Test
    void ordinaryUriKeepsExactMessage() throws Exception
    {
        MockHttpServletResponse response = commence("GET", "/api/v1/admin/app-users");
        String body = response.getContentAsString();
        assertTrue(body.contains("请求访问：/api/v1/admin/app-users，认证失败，无法访问系统资源"),
                "普通路径提示文案不得改变: " + body);
        assertEquals(200, response.getStatus());
    }

    @Test
    void nullRequestFallsBackToPlaceholderWithoutLeaking() throws Exception
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AuthenticationEntryPointImpl().commence(null, response,
                new InsufficientAuthenticationException("anonymous"));
        String body = response.getContentAsString();
        assertTrue(body.contains("{redacted}"), "空请求也必须回退占位符: " + body);
        assertEquals(200, response.getStatus());
    }
}
