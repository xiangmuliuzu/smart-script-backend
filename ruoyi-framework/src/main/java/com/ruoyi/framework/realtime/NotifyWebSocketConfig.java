package com.ruoyi.framework.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册站内实时通知的 WebSocket 端点 {@code /ws/notify}。
 *
 * <p>路径不带 {@code /api/v1} 前缀，因此不会进入 App 凭证域的过滤链，
 * 由默认安全链放行、握手拦截器完成鉴权（见 NotifyHandshakeInterceptor）。</p>
 */
@Configuration
@EnableWebSocket
public class NotifyWebSocketConfig implements WebSocketConfigurer
{
    /** 站内事件长连接端点，浏览器与 APP 共用。 */
    public static final String ENDPOINT = "/ws/notify";

    private final NotifyWebSocketHandler handler;

    private final NotifyHandshakeInterceptor handshakeInterceptor;

    public NotifyWebSocketConfig(NotifyWebSocketHandler handler, NotifyHandshakeInterceptor handshakeInterceptor)
    {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry)
    {
        // 开发态三端各自起 Vite/模拟器，来源不固定；与现有 CORS 的放行口径保持一致。
        registry.addHandler(handler, ENDPOINT)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
