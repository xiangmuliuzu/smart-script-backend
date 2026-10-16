package com.ruoyi.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.ruoyi.web.filter.A4WriteQueryParamFilter.QueryParamHiddenRequest;

/**
 * H13-REV-02（第 13 批复核补修）：A4 写接口查询参数隔离。
 *
 * 若依 `LogAspect` 在 POST/PUT 带查询参数时改记查询参数、绕过请求体指纹，
 * 故对 A4 管理域非 GET 请求屏蔽查询参数。测试覆盖包装行为与过滤范围。
 */
class A4WriteQueryParamFilterTest
{
    @Test
    void hidesQueryParamsForWrappedRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/notifications");
        request.setQueryString("requestId=SYNTHETIC_QUERY_TOKEN");
        request.addParameter("requestId", "SYNTHETIC_QUERY_TOKEN");

        QueryParamHiddenRequest wrapped = new QueryParamHiddenRequest(request);

        assertTrue(wrapped.getParameterMap().isEmpty(), "查询参数必须对下游不可见");
        assertNull(wrapped.getParameter("requestId"), "getParameter 必须返回 null");
        assertNull(wrapped.getParameterValues("requestId"), "getParameterValues 必须返回 null");
        assertFalse(wrapped.getParameterNames().hasMoreElements(), "参数名枚举必须为空");
        // 方法、URI、请求体等不受影响
        assertEquals("POST", wrapped.getMethod());
        assertEquals("/api/v1/admin/notifications", wrapped.getRequestURI());
    }

    @Test
    void filterScopeIsLimitedToA4AdminWriteRequests() throws Exception
    {
        A4WriteQueryParamFilter filter = new A4WriteQueryParamFilter();

        assertTrue(shouldNotFilter(filter, "GET", "/api/v1/admin/notifications", "pageNum=1"),
                "GET 列表接口必须保留查询参数");
        assertTrue(shouldNotFilter(filter, "POST", "/api/v1/admin/notifications", null),
                "无查询串无需包装");
        assertTrue(shouldNotFilter(filter, "POST", "/system/user", "a=b"),
                "若依原生接口不受影响");
        // C 模块 TradeController 同样挂在 /api/v1/admin 下，必须不在范围内
        assertTrue(shouldNotFilter(filter, "POST", "/api/v1/admin/trade/works", "a=b"),
                "C 模块 /api/v1/admin/trade/** 不受影响");
        assertTrue(shouldNotFilter(filter, "PUT", "/api/v1/admin/trade/quote/1/accept", "a=b"),
                "C 模块 PUT 不受影响");
        assertFalse(shouldNotFilter(filter, "POST", "/api/v1/admin/notifications", "requestId=x"),
                "A4 写接口带查询参数必须包装");
        assertFalse(shouldNotFilter(filter, "PUT", "/api/v1/admin/app-users/1/status", "a=b"),
                "A4 PUT 写接口带查询参数必须包装");
        assertFalse(shouldNotFilter(filter, "POST", "/api/v1/admin/material-refs/redeem", "x=1"),
                "A4 兑换接口带查询参数必须包装");
        assertFalse(shouldNotFilter(filter, "PUT", "/api/v1/admin/feedback/1/handle", "x=1"),
                "A4 反馈处理带查询参数必须包装");
    }

    private static boolean shouldNotFilter(A4WriteQueryParamFilter filter, String method, String uri, String query)
            throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (query != null)
        {
            request.setQueryString(query);
        }
        Method m = A4WriteQueryParamFilter.class.getDeclaredMethod("shouldNotFilter", HttpServletRequest.class);
        m.setAccessible(true);
        return (boolean) m.invoke(filter, request);
    }
}
