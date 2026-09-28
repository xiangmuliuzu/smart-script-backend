package com.ruoyi.web.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ruoyi.framework.aspectj.OperLogSanitizer;

/**
 * A4 PC 管理写接口的**查询参数隔离**（H13-REV-02，第 13 批复核补修）。
 *
 * <p>缺陷：若依 {@code LogAspect.setRequestValue} 仅在请求参数为空时序列化已脱敏的请求体；
 * 一旦 POST/PUT 带非空查询参数，就改为序列化查询参数。A4 写接口全部以 JSON 请求体为契约输入
 * （《A4-管理接口契约》§5），查询参数不是契约输入，却可被原样写入 {@code oper_param}
 * （如 {@code POST /notifications?requestId=<令牌>}），绕过请求体的值级指纹。</p>
 *
 * <p>修复：只对 **A4 PC 管理域**（见 {@link #A4_PREFIXES}）的**非 GET** 请求，把查询参数从请求中
 * 屏蔽（不改变请求体绑定、不改变任何响应与业务结果）。于是操作日志回落到「序列化已经指纹化的
 * 请求体」这条路径，查询参数（无论键名是否为契约字段）都不可能进入日志。GET 列表接口仍使用
 * 查询参数，不受影响。</p>
 *
 * <p>边界：前缀精确限定为 A4 管理接口——同一 {@code /api/v1/admin} 前缀下的 C 模块
 * （{@code /api/v1/admin/trade/**}）与若依原生接口均不在范围内；不改 {@code LogAspect} 等框架代码，
 * 仅沿既有过滤器链注入一个 A 模块自有的请求包装。</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 200)
public class A4WriteQueryParamFilter extends OncePerRequestFilter
{
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        // A4 路径白名单与 OperLogSanitizer 共用，避免两处列表漂移；
        // C 模块 /api/v1/admin/trade/** 不在白名单内，不受影响。
        if (!OperLogSanitizer.isA4AdminPath(request.getRequestURI()))
        {
            return true;
        }
        String method = request.getMethod();
        // GET/HEAD/OPTIONS 保留查询参数（列表接口依赖它）；无查询串则无需包装。
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method))
        {
            return true;
        }
        String query = request.getQueryString();
        return query == null || query.isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        chain.doFilter(new QueryParamHiddenRequest(request), response);
    }

    /** 对下游隐藏查询参数的请求包装；请求体、头、路径变量均不受影响。 */
    static final class QueryParamHiddenRequest extends HttpServletRequestWrapper
    {
        QueryParamHiddenRequest(HttpServletRequest request)
        {
            super(request);
        }

        @Override
        public String getParameter(String name)
        {
            return null;
        }

        @Override
        public String[] getParameterValues(String name)
        {
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames()
        {
            return Collections.emptyEnumeration();
        }

        @Override
        public Map<String, String[]> getParameterMap()
        {
            return Collections.emptyMap();
        }
    }
}
