package com.ruoyi.framework.realtime;

import java.util.Map;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 通知长连接握手鉴权：复用与 HTTP 相同的 RuoYi 会话令牌，鉴权不通过直接拒绝升级。
 */
@Component
public class NotifyHandshakeInterceptor implements HandshakeInterceptor
{
    private static final Logger log = LoggerFactory.getLogger(NotifyHandshakeInterceptor.class);

    public static final String ATTR_USER_ID = "notifyUserId";

    public static final String ATTR_SESSION_UUID = "notifySessionUuid";

    public static final String ATTR_DOMAIN = "notifyCredentialDomain";

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public NotifyHandshakeInterceptor(TokenService tokenService)
    {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes)
    {
        HttpServletRequest servletRequest = request instanceof ServletServerHttpRequest servlet
                ? servlet.getServletRequest() : null;
        String token = servletRequest == null ? null : resolveToken(servletRequest);
        if (token == null || token.isEmpty())
        {
            log.debug("WebSocket handshake rejected: missing token");
            return reject(response);
        }
        LoginUser session = tokenService.getLoginUser(token);
        if (session == null || session.getUserId() == null)
        {
            log.debug("WebSocket handshake rejected: invalid or expired token on {}", request.getURI().getPath());
            return reject(response);
        }
        attributes.put(ATTR_USER_ID, session.getUserId());
        attributes.put(ATTR_SESSION_UUID, session.getToken());
        attributes.put(ATTR_DOMAIN, session.getCredentialDomain());
        return true;
    }

    /**
     * Spring 在拦截器返回 false 时不会改写状态码，默认 200 会让客户端以为已经连上；
     * 显式 403 才能让前端区分「令牌问题」和「网络问题」。
     */
    private static boolean reject(ServerHttpResponse response)
    {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception)
    {
        // 无需处理
    }

    /**
     * 令牌来源优先级：Authorization 头（Flutter/原生客户端）优先，其次 token / access_token 查询参数。
     * 浏览器的 WebSocket 无法自定义请求头，Web 端只能走查询参数，因此两种都要支持。
     */
    private String resolveToken(HttpServletRequest request)
    {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX))
        {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        String value = request.getParameter("token");
        return value != null ? value : request.getParameter("access_token");
    }
}
