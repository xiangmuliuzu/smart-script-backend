package com.ruoyi.framework.web.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;

class LoginSessionRegistryTest
{
    private static final String INDEX_KEY = CacheConstants.LOGIN_USER_SESSIONS_KEY + "app:8";

    private final Map<String, LinkedHashMap<String, Double>> zsets = new HashMap<>();
    private final Set<String> sessionKeys = new HashSet<>();
    private RedisCache redisCache;
    private LoginSessionRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup()
    {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        ZSetOperations<String, Object> ops = mock(ZSetOperations.class);
        when(template.opsForZSet()).thenReturn(ops);

        when(ops.add(anyString(), any(), anyDouble())).thenAnswer(i -> {
            zsets.computeIfAbsent(i.getArgument(0), key -> new LinkedHashMap<>())
                    .put(String.valueOf((Object) i.getArgument(1)), i.getArgument(2));
            return true;
        });
        when(ops.rangeWithScores(anyString(), anyLong(), anyLong())).thenAnswer(i -> {
            Map<String, Double> set = zsets.getOrDefault(i.getArgument(0), new LinkedHashMap<>());
            return set.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(e -> (ZSetOperations.TypedTuple<Object>) new DefaultTypedTuple<Object>(e.getKey(), e.getValue()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        });
        when(ops.remove(anyString(), any())).thenAnswer(i -> {
            Map<String, Double> set = zsets.get(i.getArgument(0));
            return set != null && set.remove(String.valueOf((Object) i.getArgument(1))) != null ? 1L : 0L;
        });
        when(ops.zCard(anyString())).thenAnswer(i -> {
            Map<String, Double> set = zsets.get(i.getArgument(0));
            return set == null ? 0L : (long) set.size();
        });

        redisCache = mock(RedisCache.class);
        redisCache.redisTemplate = template;
        when(redisCache.hasKey(anyString())).thenAnswer(i -> sessionKeys.contains(i.getArgument(0)));
        when(redisCache.deleteObject(anyString())).thenAnswer(i -> {
            String key = i.getArgument(0);
            zsets.remove(key);
            return sessionKeys.remove(key);
        });

        registry = new LoginSessionRegistry();
        ReflectionTestUtils.setField(registry, "redisCache", redisCache);
    }

    private LoginUser session(String token, long expireAt)
    {
        SysUser user = new SysUser();
        user.setUserId(8L);
        user.setUserName("app-user");
        LoginUser loginUser = new LoginUser(8L, null, user, Set.of());
        loginUser.setCredentialDomain("app");
        loginUser.setToken(token);
        loginUser.setExpireTime(expireAt);
        sessionKeys.add(CacheConstants.LOGIN_TOKEN_KEY + token);
        return loginUser;
    }

    @Test
    void addWritesIndexAndRefreshesTtl()
    {
        registry.add(session("t1", System.currentTimeMillis() + 30 * 60_000L));

        assertEquals(Set.of("t1"), zsets.get(INDEX_KEY).keySet());
        verify(redisCache).expire(eq(INDEX_KEY), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    void trimEvictsLeastRecentlyActiveSession()
    {
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 4; i++)
        {
            registry.add(session("t" + i, now + i * 60_000L));
        }

        List<String> evicted = registry.trimTo("app", 8L, 3, "t4");

        assertEquals(List.of("t1"), evicted);
        assertFalse(sessionKeys.contains(CacheConstants.LOGIN_TOKEN_KEY + "t1"));
        assertEquals(Set.of("t2", "t3", "t4"), zsets.get(INDEX_KEY).keySet());
    }

    @Test
    void trimNeverEvictsCurrentToken()
    {
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 4; i++)
        {
            registry.add(session("t" + i, now + i * 60_000L));
        }

        List<String> evicted = registry.trimTo("app", 8L, 3, "t1");

        assertEquals(List.of("t2"), evicted);
        assertTrue(sessionKeys.contains(CacheConstants.LOGIN_TOKEN_KEY + "t1"));
        assertFalse(sessionKeys.contains(CacheConstants.LOGIN_TOKEN_KEY + "t2"));
    }

    @Test
    void trimDropsDeadMembersWithoutCountingThem()
    {
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 4; i++)
        {
            registry.add(session("t" + i, now + i * 60_000L));
        }
        sessionKeys.remove(CacheConstants.LOGIN_TOKEN_KEY + "t1");

        List<String> evicted = registry.trimTo("app", 8L, 3, "t4");

        assertTrue(evicted.isEmpty());
        assertEquals(Set.of("t2", "t3", "t4"), zsets.get(INDEX_KEY).keySet());
    }

    @Test
    void removeDeletesEmptyIndex()
    {
        LoginUser session = session("t1", System.currentTimeMillis() + 30 * 60_000L);
        registry.add(session);

        registry.remove(session);

        assertNull(zsets.get(INDEX_KEY));
        verify(redisCache).deleteObject(INDEX_KEY);
    }

    @Test
    void trimIgnoresDisabledLimitAndOtherUsers()
    {
        registry.add(session("t1", System.currentTimeMillis() + 30 * 60_000L));

        assertTrue(registry.trimTo("app", 8L, 0, "t1").isEmpty());
        assertTrue(registry.trimTo("app", 9L, 3, "t1").isEmpty());
        assertTrue(sessionKeys.contains(CacheConstants.LOGIN_TOKEN_KEY + "t1"));
    }
}
