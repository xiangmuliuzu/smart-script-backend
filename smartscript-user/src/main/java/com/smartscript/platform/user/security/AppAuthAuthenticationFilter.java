package com.smartscript.platform.user.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.user.exception.AppAuthException;
import com.ruoyi.framework.security.handle.AuthenticationFailureAuditor;

/**
 * Validates App Access Tokens for App credential-domain endpoints.
 * PC tokens fail domain checks and never establish App identity.
 *
 * A5：本过滤器所在的链已覆盖 /api/v1/auth、/api/v1/users、/api/v1/messages、
 * /api/v1/feedback。凡是进入本链、且不是公开端点的请求都必须先建立 App 身份，
 * 因此判定必须以「是否公开」为准，而不是逐个列举私有路径——
 * 逐个列举会在新增用户中心接口时漏配，使请求没有身份并被 401 拒绝。
 */
public class AppAuthAuthenticationFilter extends OncePerRequestFilter
{
    private final AppAccessTokenService accessTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 过滤器链层 401 审计回调（H9-LOG-04 残余，第 11 批）。可为 null（单测/未装配时）。
     * 本过滤器直接短路写响应，不进入 MVC，故必须在此处回调审计。
     */
    private final AuthenticationFailureAuditor authFailureAuditor;

    /**
     * 完全公开端点（与 AppAuthSecurityConfig 的 permitAll 规则一致）：
     * 这些路径本来就不接受 App Token，过滤器不为其解析身份。
     */
    private static final String[] PUBLIC_PATHS = {
            "/auth/sms/send",
            "/auth/sms/login",
            "/auth/password/login",
            "/auth/password/reset",
            "/auth/register",
            "/auth/token/refresh",
            "/auth/agreements",
            "/auth/oauth/wechat/login",
            "/auth/oauth/qq/login",
            "/pc-auth/login",
    };

    /**
     * 「可匿名但可带身份」的端点（A6 起引入）。
     *
     * 与 {@link #PUBLIC_PATHS} 的区别：这些接口**不要求** App Token，但客户端若带了
     * 合法 Token，就必须建立身份（否则已登录用户在公开接口上会退化为游客，
     * 无法获得个性化结果）。因此它们不跳过过滤器，而是「有 Token 就解析，
     * 无 Token 则按游客继续」。
     */
    private static final String[] OPTIONAL_IDENTITY_PATHS = {
            // 业务模块（B/C/D/E）的公开接口
            // B 模块书城浏览主链路（第一批）：Banner / 分类 / 标签 / 作品列表与详情 / 榜单。
            // 均为只读公开接口，游客可读；已登录用户带 Token 时仍建立身份，便于后续按身份返回个性化字段。
            "/content/banners",
            "/content/categories",
            "/content/tags",
            "/content/works",
            "/content/rankings",
            // B 模块试读链路（第二批）：章节目录落在 /content/works/** 内，
            // 章节正文为独立路径 /content/chapters/{chapterId}，必须在此登记，
            // 否则过滤器会先于授权规则按「需要身份」拒绝（401），permitAll 规则永远不生效。
            "/content/chapters",
    };

    public AppAuthAuthenticationFilter(AppAccessTokenService accessTokenService)
    {
        this(accessTokenService, null);
    }

    public AppAuthAuthenticationFilter(AppAccessTokenService accessTokenService, AuthenticationFailureAuditor authFailureAuditor)
    {
        this.accessTokenService = accessTokenService;
        this.authFailureAuditor = authFailureAuditor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException
    {
        String uri = request.getRequestURI();
        if (!requiresAppIdentity(uri))
        {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing instanceof AppIdentityContext)
        {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        boolean optional = allowsOptionalIdentity(uri);
        if (header == null || !header.startsWith("Bearer "))
        {
            if (optional)
            {
                // 公开接口未带 Token：按游客继续，由业务层返回游客视角的数据
                filterChain.doFilter(request, response);
                return;
            }
            writeError(request, response, AppAuthErrorCodes.UNAUTHORIZED, "unauthorized");
            return;
        }
        String token = header.substring(7).trim();
        try
        {
            AppIdentityContext identity = accessTokenService.parseAndValidate(token);
            SecurityContextHolder.getContext().setAuthentication(identity);
            filterChain.doFilter(request, response);
        }
        catch (AppAuthException e)
        {
            writeError(request, response, e.getCode(), e.getMessage());
        }
    }

    /**
     * 是否允许「带 Token 则解析、无 Token 则按游客继续」。
     */
    static boolean allowsOptionalIdentity(String uri)
    {
        String path = normalize(uri);
        if (path == null || path.isEmpty())
        {
            return false;
        }
        for (String optionalPath : OPTIONAL_IDENTITY_PATHS)
        {
            if (path.equals(optionalPath) || path.startsWith(optionalPath + "/"))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否需要先建立 App 身份：App 凭证域内且非公开端点。
     *
     * 保守取向：无法判定的路径按「需要身份」处理。宁可让未带 Token 的请求得到 401，
     * 也不能让私有接口在无身份的情况下进入业务层。
     */
    static boolean requiresAppIdentity(String uri)
    {
        String path = normalize(uri);
        if (path == null || path.isEmpty())
        {
            return false;
        }
        for (String publicPath : PUBLIC_PATHS)
        {
            if (path.equals(publicPath) || path.startsWith(publicPath + "/"))
            {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String uri)
    {
        if (uri == null)
        {
            return null;
        }
        return uri.startsWith("/api/v1") ? uri.substring("/api/v1".length()) : uri;
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, int code, String message) throws IOException
    {
        int status = code == AppAuthErrorCodes.ACCOUNT_DISABLED || code == AppAuthErrorCodes.DOMAIN_OR_PERMISSION
                ? 403
                : 401;
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(AppApiResponse.fail(code, message)));
        // 响应写完之后才回调审计；回调只读请求、不得改动响应（见 AuthenticationFailureAuditor 约定）。
        // 第 15 批：403（凭证域不符/账号禁用）按事件类型携带 reason，由审计器落一条可定位的安全事件。
        if (authFailureAuditor != null)
        {
            authFailureAuditor.record(request, AuthenticationFailureAuditor.DOMAIN_APP, status, auditReason(code));
        }
    }

    /**
     * 把 App 业务错误码映射为审计事件类型标记；401 类不携带（沿用第 11 批 401 口径）。
     */
    static String auditReason(int code)
    {
        if (code == AppAuthErrorCodes.DOMAIN_OR_PERMISSION)
        {
            return AuthenticationFailureAuditor.REASON_DOMAIN_OR_PERMISSION;
        }
        if (code == AppAuthErrorCodes.ACCOUNT_DISABLED)
        {
            return AuthenticationFailureAuditor.REASON_ACCOUNT_DISABLED;
        }
        return null;
    }
}
