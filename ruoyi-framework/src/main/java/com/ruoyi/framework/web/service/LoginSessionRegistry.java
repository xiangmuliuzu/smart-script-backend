package com.ruoyi.framework.web.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.StringUtils;

/**
 * 按「凭据域 + 用户」维护登录会话索引，供设备数上限裁剪使用。
 *
 * <p>索引与 {@link CacheConstants#LOGIN_TOKEN_KEY} 使用不同前缀：现有
 * {@code revokeUserSessions}/{@code SysUserOnlineController} 会对 {@code login_tokens:*}
 * 做全量扫描并按 LoginUser 反序列化，索引键若混入该命名空间会反序列化失败。</p>
 */
@Component
public class LoginSessionRegistry
{
    private static final Logger log = LoggerFactory.getLogger(LoginSessionRegistry.class);

    /** 索引 TTL 比会话多保留 1 小时，避免索引先于会话过期。 */
    private static final int INDEX_TTL_BUFFER_MINUTES = 60;

    @Autowired
    private RedisCache redisCache;

    /**
     * 写入/更新会话索引。创建与续期共用；score 使用会话过期时间，
     * 续期后 score 变大，最久未活跃的会话始终排在前面。
     */
    @SuppressWarnings("unchecked")
    public void add(LoginUser loginUser)
    {
        if (loginUser == null || loginUser.getUserId() == null
                || StringUtils.isEmpty(loginUser.getToken()) || loginUser.getExpireTime() == null)
        {
            return;
        }
        String key = key(loginUser);
        ZSetOperations<String, Object> ops = redisCache.redisTemplate.opsForZSet();
        ops.add(key, loginUser.getToken(), loginUser.getExpireTime().doubleValue());
        long ttlMinutes = Math.max(1L, (loginUser.getExpireTime() - System.currentTimeMillis()) / 60000L)
                + INDEX_TTL_BUFFER_MINUTES;
        redisCache.expire(key, ttlMinutes, TimeUnit.MINUTES);
    }

    /** 退出/吊销单个会话后同步索引。 */
    public void remove(LoginUser loginUser)
    {
        if (loginUser == null)
        {
            return;
        }
        remove(loginUser.getCredentialDomain(), loginUser.getUserId(), loginUser.getToken());
    }

    public void remove(String domain, Long userId, String token)
    {
        if (userId == null || StringUtils.isEmpty(token))
        {
            return;
        }
        String key = key(domain, userId);
        redisCache.redisTemplate.opsForZSet().remove(key, token);
        deleteIndexIfEmpty(key);
    }

    /**
     * 裁剪到最多 {@code max} 个会话：先剔除 token 已不存在的死成员，
     * 再按 score 升序删除最久未活跃的会话，当前新会话（{@code keepToken}）不参与。
     *
     * @return 被踢除的会话 UUID
     */
    @SuppressWarnings("unchecked")
    public List<String> trimTo(String domain, Long userId, int max, String keepToken)
    {
        if (max <= 0 || userId == null)
        {
            return List.of();
        }
        String key = key(domain, userId);
        ZSetOperations<String, Object> ops = redisCache.redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<Object>> tuples = ops.rangeWithScores(key, 0, -1);
        if (tuples == null || tuples.isEmpty())
        {
            return List.of();
        }
        List<ZSetOperations.TypedTuple<Object>> alive = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<Object> tuple : tuples)
        {
            String token = tuple.getValue() == null ? null : String.valueOf(tuple.getValue());
            if (StringUtils.isEmpty(token)
                    || !Boolean.TRUE.equals(redisCache.hasKey(CacheConstants.LOGIN_TOKEN_KEY + token)))
            {
                ops.remove(key, tuple.getValue());
                continue;
            }
            alive.add(tuple);
        }
        int overflow = alive.size() - max;
        if (overflow <= 0)
        {
            deleteIndexIfEmpty(key);
            return List.of();
        }
        List<String> evicted = new ArrayList<>(overflow);
        for (ZSetOperations.TypedTuple<Object> tuple : alive)
        {
            if (overflow <= 0)
            {
                break;
            }
            String token = String.valueOf(tuple.getValue());
            if (Objects.equals(token, keepToken))
            {
                continue;
            }
            redisCache.deleteObject(CacheConstants.LOGIN_TOKEN_KEY + token);
            ops.remove(key, tuple.getValue());
            evicted.add(token);
            overflow--;
        }
        deleteIndexIfEmpty(key);
        if (!evicted.isEmpty())
        {
            log.info("Evicted {} least-active login session(s) in domain {}", evicted.size(), domain);
        }
        return evicted;
    }

    private void deleteIndexIfEmpty(String key)
    {
        if (Long.valueOf(0L).equals(redisCache.redisTemplate.opsForZSet().zCard(key)))
        {
            redisCache.deleteObject(key);
        }
    }

    private String key(LoginUser loginUser)
    {
        return key(loginUser.getCredentialDomain(), loginUser.getUserId());
    }

    private String key(String domain, Long userId)
    {
        String resolvedDomain = StringUtils.isEmpty(domain) ? "admin" : domain;
        return CacheConstants.LOGIN_USER_SESSIONS_KEY + resolvedDomain + ":" + userId;
    }
}
