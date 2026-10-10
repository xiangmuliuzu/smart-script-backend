package com.smartscript.platform.user.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.framework.security.handle.AuthenticationFailureAuditor;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.api.AppApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AppAuthEntryPoint implements AuthenticationEntryPoint
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 过滤器链层 401 审计回调（H9-LOG-04 残余，第 11 批）。可选注入。
     *
     * 本入口点是 App 链的「安全网」：私有路径通常已被 {@link AppAuthAuthenticationFilter}
     * 短路，故它极少触发；仍接入以覆盖未来配置变化，并靠审计器的一次性标记避免与过滤器重复记账。
     */
    @Autowired(required = false)
    private AuthenticationFailureAuditor authenticationFailureAuditor;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            org.springframework.security.core.AuthenticationException authException) throws IOException, ServletException
    {
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                AppApiResponse.fail(AppAuthErrorCodes.UNAUTHORIZED, "unauthorized")));
        // 响应写完之后才回调审计；回调只读请求、不得改动响应。
        if (authenticationFailureAuditor != null)
        {
            authenticationFailureAuditor.record(request, AuthenticationFailureAuditor.DOMAIN_APP, 401);
        }
    }
}
