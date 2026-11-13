package com.ruoyi.framework.realtime;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 通知长连接处理器：只负责连接生命周期与心跳应答，业务帧由 RealtimePushService 写入。
 *
 * <p>客户端可以发的唯一帧是 ping。服务端不回显任何客户端文本内容，
 * 避免把这条通道变成可被业务消息污染的广播源。</p>
 */
@Component
public class NotifyWebSocketHandler extends TextWebSocketHandler
{
    private static final Logger log = LoggerFactory.getLogger(NotifyWebSocketHandler.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OnlineSessionRegistry registry;

    public NotifyWebSocketHandler(OnlineSessionRegistry registry)
    {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
    {
        Map<String, Object> attributes = session.getAttributes();
        Long userId = (Long) attributes.get(NotifyHandshakeInterceptor.ATTR_USER_ID);
        String loginSessionUuid = (String) attributes.get(NotifyHandshakeInterceptor.ATTR_SESSION_UUID);
        if (userId == null)
        {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        registry.register(userId, session, loginSessionUuid);
        write(session, "{\"type\":\"connected\",\"userId\":" + userId + ",\"serverTime\":"
                + System.currentTimeMillis() + "}");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
    {
        registry.touch(session.getId());
        if (isPing(message.getPayload()))
        {
            write(session, "{\"type\":\"pong\",\"serverTime\":" + System.currentTimeMillis() + "}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
    {
        registry.unregister(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
    {
        log.debug("Notification connection error for session {} ({})", session.getId(),
                exception.getClass().getSimpleName());
        registry.unregister(session.getId());
    }

    private static boolean isPing(String payload)
    {
        if (payload == null)
        {
            return false;
        }
        String trimmed = payload.trim();
        if ("ping".equals(trimmed))
        {
            return true;
        }
        try
        {
            JsonNode node = MAPPER.readTree(trimmed);
            return node != null && "ping".equals(node.path("type").asText());
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private void write(WebSocketSession session, String payload)
    {
        try
        {
            synchronized (session)
            {
                if (session.isOpen())
                {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        }
        catch (Exception e)
        {
            log.debug("Failed to write handshake frame to {}", session.getId());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status)
    {
        try
        {
            session.close(status);
        }
        catch (Exception e)
        {
            log.trace("Unable to close unauthenticated notification connection", e);
        }
    }
}
