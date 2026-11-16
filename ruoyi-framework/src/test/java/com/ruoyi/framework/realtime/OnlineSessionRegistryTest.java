package com.ruoyi.framework.realtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;

/**
 * 在线连接注册表：多端投递、写失败摘除、以及登录会话失效后的连接回收。
 */
class OnlineSessionRegistryTest
{
    private static final long USER_ID = 88L;

    private RedisCache redisCache;

    private OnlineSessionRegistry registry;

    @BeforeEach
    void setup()
    {
        redisCache = mock(RedisCache.class);
        registry = new OnlineSessionRegistry(redisCache, mock(ScheduledExecutorService.class));
    }

    @Test
    void sendDeliversToEveryDeviceOfTheSameUser() throws Exception
    {
        WebSocketSession web = openSession("s-web");
        WebSocketSession app = openSession("s-app");
        registry.register(USER_ID, web, "uuid-1");
        registry.register(USER_ID, app, "uuid-1");

        assertTrue(registry.send(USER_ID, "{\"type\":\"chat\"}"));

        ArgumentCaptor<TextMessage> webFrame = ArgumentCaptor.forClass(TextMessage.class);
        verify(web).sendMessage(webFrame.capture());
        assertEquals("{\"type\":\"chat\"}", webFrame.getValue().getPayload());
        verify(app).sendMessage(any(TextMessage.class));
        assertEquals(1, registry.onlineUserCount());
    }

    @Test
    void sendReturnsFalseWhenUserHasNoConnection()
    {
        assertFalse(registry.send(USER_ID, "{}"));
        assertFalse(registry.send(null, "{}"));
    }

    @Test
    void unregisterRemovesOnlyThatDevice() throws Exception
    {
        WebSocketSession web = openSession("s-web");
        WebSocketSession app = openSession("s-app");
        registry.register(USER_ID, web, "uuid-1");
        registry.register(USER_ID, app, "uuid-1");

        registry.unregister("s-web");
        assertTrue(registry.send(USER_ID, "{}"), "另一台设备仍应可投递");
        verify(app).sendMessage(any(TextMessage.class));
    }

    @Test
    void writeFailureDropsTheConnection() throws Exception
    {
        WebSocketSession broken = mock(WebSocketSession.class);
        when(broken.getId()).thenReturn("s-broken");
        when(broken.isOpen()).thenReturn(true);
        doThrow(new IOException("connection reset")).when(broken).sendMessage(any(TextMessage.class));
        registry.register(USER_ID, broken, "uuid-1");

        assertFalse(registry.send(USER_ID, "{}"));
        verify(broken).close(any(CloseStatus.class));
        assertEquals(0, registry.onlineUserCount());
        assertFalse(registry.send(USER_ID, "{}"), "已摘除的连接不应再被投递");
    }

    /**
     * 退出登录/被踢会删除 Redis 里的 login_tokens 会话，长连接必须跟着断开，
     * 否则被撤销的身份还能继续收到事件帧。
     */
    @Test
    void sweepClosesConnectionWhoseLoginSessionIsGone() throws Exception
    {
        WebSocketSession session = openSession("s-1");
        registry.register(USER_ID, session, "uuid-gone");
        when(redisCache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + "uuid-gone")).thenReturn(null);

        registry.sweep();

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        assertEquals(0, registry.onlineUserCount());
    }

    @Test
    void sweepKeepsAliveConnections() throws Exception
    {
        WebSocketSession session = openSession("s-1");
        registry.register(USER_ID, session, "uuid-live");
        when(redisCache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + "uuid-live")).thenReturn("session");

        registry.sweep();

        verify(session, never()).close(any(CloseStatus.class));
        assertEquals(1, registry.onlineUserCount());
    }

    /** Redis 抖动不应误杀连接。 */
    @Test
    @SuppressWarnings("unchecked")
    void sweepKeepsConnectionsWhenLivenessCheckFails() throws Exception
    {
        WebSocketSession session = openSession("s-1");
        registry.register(USER_ID, session, "uuid-x");
        when(redisCache.getCacheObject(anyString())).thenThrow(new IllegalStateException("redis down"));

        registry.sweep();

        verify(session, never()).close(any(CloseStatus.class));
        assertEquals(1, registry.onlineUserCount());
    }

    private static WebSocketSession openSession(String id) throws IOException
    {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
