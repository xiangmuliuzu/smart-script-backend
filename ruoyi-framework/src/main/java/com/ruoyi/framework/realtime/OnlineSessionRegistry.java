package com.ruoyi.framework.realtime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 在线通知连接注册表：userId -> 该用户当前所有在线端（同一账号 PC + APP 并存是常态）。
 *
 * <p>这里只承载「谁在线」和「往在线端写一帧」两件事。消息本体始终先落库再推送，
 * 因此本注册表不是消息存储，连接不在线不构成消息丢失。</p>
 */
@Component
public class OnlineSessionRegistry
{
    private static final Logger log = LoggerFactory.getLogger(OnlineSessionRegistry.class);

    /** 超过该时长没有任何客户端帧视为半开连接（切网络后 TCP 不会立刻报错），由清扫任务关闭。 */
    private static final long IDLE_TIMEOUT_MILLIS = 90_000L;

    /** 清扫周期；同时用于校验 Redis 登录会话是否仍存活（退出登录/被踢后不应继续收到帧）。 */
    private static final long SWEEP_PERIOD_SECONDS = 60L;

    private final RedisCache redisCache;

    private final ScheduledExecutorService scheduler;

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Connection>> byUser = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> userBySession = new ConcurrentHashMap<>();

    public OnlineSessionRegistry(RedisCache redisCache,
            @Qualifier("scheduledExecutorService") ScheduledExecutorService scheduler)
    {
        this.redisCache = redisCache;
        this.scheduler = scheduler;
    }

    /** 一条已建立的通知连接。lastActive 由收发两侧更新，仅用于空闲判定。 */
    static final class Connection
    {
        private final WebSocketSession session;

        private final String loginSessionUuid;

        private volatile long lastActiveAt;

        Connection(WebSocketSession session, String loginSessionUuid)
        {
            this.session = session;
            this.loginSessionUuid = loginSessionUuid;
            this.lastActiveAt = System.currentTimeMillis();
        }

        void touch()
        {
            this.lastActiveAt = System.currentTimeMillis();
        }

        boolean isIdle(long now)
        {
            return now - lastActiveAt > IDLE_TIMEOUT_MILLIS;
        }
    }

    @PostConstruct
    public void startSweeper()
    {
        scheduler.scheduleWithFixedDelay(this::sweep, SWEEP_PERIOD_SECONDS, SWEEP_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown()
    {
        byUser.values().forEach(connections -> connections.values()
                .forEach(c -> close(c, CloseStatus.SERVICE_RESTARTED)));
        byUser.clear();
        userBySession.clear();
    }

    public void register(Long userId, WebSocketSession session, String loginSessionUuid)
    {
        if (userId == null || session == null)
        {
            return;
        }
        Connection connection = new Connection(session, loginSessionUuid);
        byUser.computeIfAbsent(userId, key -> new ConcurrentHashMap<>()).put(session.getId(), connection);
        userBySession.put(session.getId(), userId);
    }

    public void unregister(String sessionId)
    {
        if (sessionId == null)
        {
            return;
        }
        Long userId = userBySession.remove(sessionId);
        if (userId == null)
        {
            return;
        }
        byUser.computeIfPresent(userId, (key, connections) ->
        {
            connections.remove(sessionId);
            return connections.isEmpty() ? null : connections;
        });
    }

    /**
     * 向某个用户的全部在线端写一帧。
     *
     * @return 是否有至少一条连接写入成功；返回 false 表示该用户此刻不在线，
     *         调用方无需补偿——客户端重连或下一次读取仍以数据库为准。
     */
    public boolean send(Long userId, String payload)
    {
        Collection<Connection> connections = connections(userId);
        if (connections.isEmpty())
        {
            return false;
        }
        boolean delivered = false;
        for (Connection connection : connections)
        {
            if (write(connection, payload))
            {
                delivered = true;
            }
        }
        return delivered;
    }

    public void touch(String sessionId)
    {
        Connection connection = findConnection(sessionId);
        if (connection != null)
        {
            connection.touch();
        }
    }

    /** 当前在线用户数，用于健康检查与本地验证。 */
    public int onlineUserCount()
    {
        return byUser.size();
    }

    private Collection<Connection> connections(Long userId)
    {
        if (userId == null)
        {
            return Collections.emptyList();
        }
        Map<String, Connection> connections = byUser.get(userId);
        // 复制一份再遍历：写一帧期间可能有新端点注册或旧端点摘除
        return connections == null ? Collections.<Connection>emptyList() : new ArrayList<>(connections.values());
    }

    private Connection findConnection(String sessionId)
    {
        Long userId = sessionId == null ? null : userBySession.get(sessionId);
        if (userId == null)
        {
            return null;
        }
        Map<String, Connection> connections = byUser.get(userId);
        return connections == null ? null : connections.get(sessionId);
    }

    /**
     * WebSocketSession#sendMessage 自身非线程安全：同一用户的多个业务事件可能并发写同一连接，
     * 因此按连接加锁串行写出，避免帧交错损坏。
     */
    private boolean write(Connection connection, String payload)
    {
        WebSocketSession session = connection.session;
        if (!session.isOpen())
        {
            drop(connection);
            return false;
        }
        try
        {
            synchronized (session)
            {
                session.sendMessage(new TextMessage(payload));
            }
            connection.touch();
            return true;
        }
        catch (IOException | IllegalStateException e)
        {
            log.debug("Notification frame dropped for user {} ({})", userBySession.get(session.getId()),
                    e.getClass().getSimpleName());
            drop(connection);
            return false;
        }
    }

    /**
     * 周期清理：空闲连接与登录会话已失效的连接都关闭。
     *
     * <p>后者是安全约束——退出登录、改密、管理员踢人都会删除 Redis 里的 login_tokens 会话，
     * 长连接必须跟着断开，否则被撤销的身份还能继续收到事件帧。</p>
     */
    void sweep()
    {
        long now = System.currentTimeMillis();
        byUser.forEach((userId, connections) -> connections.values().forEach(connection ->
        {
            if (connection.isIdle(now))
            {
                close(connection, CloseStatus.GOING_AWAY);
                return;
            }
            if (!isLoginSessionAlive(connection.loginSessionUuid))
            {
                close(connection, CloseStatus.POLICY_VIOLATION);
            }
        }));
    }

    private boolean isLoginSessionAlive(String loginSessionUuid)
    {
        if (loginSessionUuid == null)
        {
            return true;
        }
        try
        {
            return redisCache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + loginSessionUuid) != null;
        }
        catch (RuntimeException e)
        {
            // Redis 抖动不应误杀连接
            log.debug("Login session liveness check failed ({})", e.getClass().getSimpleName());
            return true;
        }
    }

    private void drop(Connection connection)
    {
        close(connection, CloseStatus.SERVER_ERROR);
    }

    private void close(Connection connection, CloseStatus status)
    {
        unregister(connection.session.getId());
        try
        {
            connection.session.close(status);
        }
        catch (IOException | IllegalStateException e)
        {
            log.trace("Closing notification connection failed", e);
        }
    }
}
