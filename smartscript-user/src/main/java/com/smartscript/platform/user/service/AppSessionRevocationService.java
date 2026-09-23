package com.smartscript.platform.user.service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.redis.RedisCache;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.mapper.AppRefreshSessionMapper;

/**
 * Central revocation for App sessions. Redis is the denylist cache; MySQL is
 * the durable source of truth for refresh sessions.
 */
@Service
public class AppSessionRevocationService
{
    private static final String SESSION_REVOKED = "session:revoked:";
    private static final String ACCESS_JTI_REVOKED = "access:jti:revoked:";

    private final AppAuthProperties properties;
    private final RedisCache redisCache;
    private final AppRefreshSessionMapper refreshSessionMapper;

    public AppSessionRevocationService(AppAuthProperties properties,
            RedisCache redisCache,
            AppRefreshSessionMapper refreshSessionMapper)
    {
        this.properties = properties;
        this.redisCache = redisCache;
        this.refreshSessionMapper = refreshSessionMapper;
    }

    public String key(String suffix)
    {
        String prefix = properties.getRedisKeyPrefix();
        if (!prefix.endsWith(":"))
        {
            prefix = prefix + ":";
        }
        return prefix + suffix;
    }

    public void markSessionRevoked(Long sessionId, String jti, long ttlSeconds)
    {
        long ttl = Math.max(ttlSeconds, 60L);
        if (sessionId != null)
        {
            redisCache.setCacheObject(key(SESSION_REVOKED + sessionId), "1", (int) ttl, TimeUnit.SECONDS);
        }
        if (jti != null && !jti.isBlank())
        {
            redisCache.setCacheObject(key(ACCESS_JTI_REVOKED + jti), "1", (int) ttl, TimeUnit.SECONDS);
        }
    }

    public boolean isSessionRevoked(Long sessionId)
    {
        if (sessionId == null)
        {
            return false;
        }
        Object v = redisCache.getCacheObject(key(SESSION_REVOKED + sessionId));
        return v != null;
    }

    public boolean isJtiRevoked(String jti)
    {
        if (jti == null || jti.isBlank())
        {
            return false;
        }
        Object v = redisCache.getCacheObject(key(ACCESS_JTI_REVOKED + jti));
        return v != null;
    }

    public void revokeAllForUser(Long userId, String reason)
    {
        Date now = new Date();
        List<com.smartscript.platform.user.domain.AppRefreshSession> active =
                refreshSessionMapper.selectActiveByUserId(userId, now);
        refreshSessionMapper.revokeByUserId(userId, reason, now);
        long ttl = properties.getAccessTokenTtlSeconds();
        if (active != null)
        {
            for (var s : active)
            {
                markSessionRevoked(s.getId(), null, ttl);
            }
        }
    }

    public void revokeSession(Long sessionId, String jti, String reason)
    {
        Date now = new Date();
        if (sessionId != null)
        {
            refreshSessionMapper.revokeById(sessionId, reason, now);
        }
        markSessionRevoked(sessionId, jti, properties.getAccessTokenTtlSeconds());
    }

    public void revokeOthersForUser(Long userId, Long keepSessionId, String reason)
    {
        Date now = new Date();
        List<com.smartscript.platform.user.domain.AppRefreshSession> active =
                refreshSessionMapper.selectActiveByUserId(userId, now);
        refreshSessionMapper.revokeOtherSessions(userId, keepSessionId, reason, now);
        long ttl = properties.getAccessTokenTtlSeconds();
        if (active != null)
        {
            for (var s : active)
            {
                if (keepSessionId != null && keepSessionId.equals(s.getId()))
                {
                    continue;
                }
                markSessionRevoked(s.getId(), null, ttl);
            }
        }
    }
}
