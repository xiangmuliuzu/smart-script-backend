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
import com.smartscript.platform.user.dto.AppApiResponse;
import com.smartscript.platform.user.exception.AppAuthException;

/**
 * Validates App Access Tokens for /api/v1/auth private endpoints.
 * PC tokens fail domain checks and never establish App identity.
 */
public class AppAuthAuthenticationFilter extends OncePerRequestFilter
{
    private final AppAccessTokenService accessTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AppAuthAuthenticationFilter(AppAccessTokenService accessTokenService)
    {
        this.accessTokenService = accessTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException
    {
        if (!isPrivateAppPath(request.getRequestURI()))
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
        if (header == null || !header.startsWith("Bearer "))
        {
            writeError(response, AppAuthErrorCodes.UNAUTHORIZED, "unauthorized");
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
            writeError(response, e.getCode(), e.getMessage());
        }
    }

    static boolean isPrivateAppPath(String uri)
    {
        if (uri == null)
        {
            return false;
        }
        String path = uri.startsWith("/api/v1") ? uri.substring("/api/v1".length()) : uri;
        return path.startsWith("/auth/logout")
                || path.startsWith("/auth/me")
                || path.startsWith("/auth/password/set")
                || path.startsWith("/auth/password/change");
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException
    {
        response.setStatus(code == AppAuthErrorCodes.ACCOUNT_DISABLED || code == AppAuthErrorCodes.DOMAIN_OR_PERMISSION
                ? 403
                : 401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(AppApiResponse.fail(code, message)));
    }
}
