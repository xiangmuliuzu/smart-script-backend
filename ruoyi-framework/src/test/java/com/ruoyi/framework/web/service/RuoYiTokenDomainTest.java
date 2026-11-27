package com.ruoyi.framework.web.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.security.filter.JwtAuthenticationTokenFilter;

class RuoYiTokenDomainTest
{
    private static final String SECRET = "RuoYi-test-signing-secret-0123456789-ABCDEFG";
    private TokenService tokens;
    private LoginSessionRegistry sessionRegistry;
    private final Map<String, Object> cache = new HashMap<>();

    @BeforeEach
    void setup()
    {
        SecurityContextHolder.clearContext();
        RedisCache redis = mock(RedisCache.class);
        when(redis.getCacheObject(anyString())).thenAnswer(i -> cache.get(i.getArgument(0)));
        doAnswer(i -> { cache.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(redis).setCacheObject(anyString(), any(), anyInt(), any());
        when(redis.setCacheObjectIfPresent(anyString(), any(), anyInt(), any())).thenAnswer(i -> {
            String key = i.getArgument(0);
            if (!cache.containsKey(key)) return false;
            cache.put(key, i.getArgument(1));
            return true;
        });
        when(redis.deleteObject(anyString())).thenAnswer(i -> cache.remove(i.getArgument(0)) != null);
        when(redis.keys(anyString())).thenAnswer(i -> java.util.List.copyOf(cache.keySet()));
        tokens = spy(new TokenService());
        ReflectionTestUtils.setField(tokens, "redisCache", redis);
        sessionRegistry = mock(LoginSessionRegistry.class);
        ReflectionTestUtils.setField(tokens, "loginSessionRegistry", sessionRegistry);
        ReflectionTestUtils.setField(tokens, "secret", SECRET);
        ReflectionTestUtils.setField(tokens, "header", "Authorization");
        ReflectionTestUtils.setField(tokens, "expireTime", 30);
        ReflectionTestUtils.setField(tokens, "appExpireTime", 7 * 24 * 60);
        doNothing().when(tokens).setUserAgent(any());
    }

    private LoginUser user(long id, String domain)
    {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setUserName("user" + id);
        user.setUserType("app".equals(domain) ? "01" : "00");
        LoginUser session = new LoginUser(id, null, user, java.util.Set.of());
        session.setCredentialDomain(domain);
        return session;
    }

    @Test
    void signedDomainMatchesRedisAndMissingSessionFails()
    {
        LoginUser app = user(8, "app");
        String jwt = tokens.createToken(app);
        assertEquals("app", io.jsonwebtoken.Jwts.parser().setSigningKey(SECRET)
                .parseClaimsJws(jwt).getBody().get("credential_domain"));
        assertSame(app, tokens.getLoginUser(jwt));
        app.setCredentialDomain("admin");
        assertNull(tokens.getLoginUser(jwt));
        app.setCredentialDomain("app");
        tokens.delLoginUser(app.getToken());
        assertNull(tokens.getLoginUser(jwt));
    }

    @Test
    void nearExpiryRenewsServerSessionAndExpiredCacheIsRejected()
    {
        LoginUser app = user(8, "app");
        String jwt = tokens.createToken(app);
        app.setExpireTime(System.currentTimeMillis() + 60_000);
        tokens.verifyToken(app);
        assertTrue(app.getExpireTime() > System.currentTimeMillis() + 6 * 24 * 60 * 60_000L);
        app.setExpireTime(System.currentTimeMillis() - 1);
        assertNull(tokens.getLoginUser(jwt));
    }

    @Test
    void appSessionSlidesToSevenDaysAndAdminStaysAtThirtyMinutes()
    {
        long now = System.currentTimeMillis();
        long sevenDays = 7 * 24 * 60 * 60_000L;

        LoginUser app = user(8, "app");
        tokens.createToken(app);
        assertTrue(app.getExpireTime() - now >= sevenDays - 5_000, "App 会话创建即 7 天有效期");

        // 用了 3 天后仍有 4 天剩余：不足「7天-1小时」，续期会把过期时间推回 now + 7 天
        app.setExpireTime(now + 4 * 24 * 60 * 60_000L);
        tokens.verifyToken(app);
        assertTrue(app.getExpireTime() - now >= sevenDays - 5_000, "App 会话按最后活跃时间滑动续期");

        // 刚续期不久（只剩 6 天 23 小时）不重复写 Redis
        long recentlyRenewed = app.getExpireTime();
        tokens.verifyToken(app);
        assertEquals(recentlyRenewed, app.getExpireTime(), "1 小时内不重复续期");

        // 管理端仍按 30 分钟 + 剩 20 分钟才续的原口径
        LoginUser admin = user(1, "admin");
        tokens.createToken(admin);
        assertTrue(admin.getExpireTime() - now <= 30 * 60_000L + 5_000);
        admin.setExpireTime(now + 25 * 60_000L);
        tokens.verifyToken(admin);
        assertEquals(now + 25 * 60_000L, admin.getExpireTime(), "管理端剩余超过 20 分钟不续期");
        admin.setExpireTime(now + 10 * 60_000L);
        tokens.verifyToken(admin);
        assertTrue(admin.getExpireTime() - now > 29 * 60_000L, "管理端剩余不足 20 分钟续到 30 分钟");
    }

    @Test
    void wrongAlgorithmOrLegacyAppJwtCannotResolveSession()
    {
        LoginUser app = user(8, "app");
        tokens.createToken(app);
        String wrongAlgorithm = io.jsonwebtoken.Jwts.builder()
                .claim("login_user_key", app.getToken()).claim("credential_domain", "app")
                .signWith(io.jsonwebtoken.SignatureAlgorithm.HS256, SECRET).compact();
        assertNull(tokens.getLoginUser(wrongAlgorithm));
        String legacy = io.jsonwebtoken.Jwts.builder().setSubject("8").claim("token_type", "app_access")
                .signWith(io.jsonwebtoken.SignatureAlgorithm.HS512, SECRET).compact();
        assertNull(tokens.getLoginUser(legacy));
    }

    @Test
    void revocationPreservesCurrentAppSessionAndAdminSession()
    {
        LoginUser current = user(8, "app"), other = user(8, "app"), admin = user(8, "admin"), outsider = user(9, "app");
        String currentJwt = tokens.createToken(current), otherJwt = tokens.createToken(other);
        String adminJwt = tokens.createToken(admin), outsiderJwt = tokens.createToken(outsider);
        tokens.revokeUserSessions(8L, "app", current.getToken());
        assertNotNull(tokens.getLoginUser(currentJwt));
        assertNull(tokens.getLoginUser(otherJwt));
        assertNotNull(tokens.getLoginUser(adminJwt));
        assertNotNull(tokens.getLoginUser(outsiderJwt));
        tokens.revokeUserSessions(8L, "app", null);
        assertNull(tokens.getLoginUser(currentJwt));
        assertNotNull(tokens.getLoginUser(adminJwt));
    }

    @Test
    void inFlightRenewalCannotReviveALoggedOutSession()
    {
        LoginUser app = user(8, "app");
        String jwt = tokens.createToken(app);
        app.setExpireTime(System.currentTimeMillis() + 60_000);
        tokens.delLoginUser(app.getToken());
        tokens.verifyToken(app);
        assertNull(tokens.getLoginUser(jwt));
    }

    @Test
    void sessionIndexTracksCreateRenewAndDelete()
    {
        LoginUser app = user(8, "app");
        tokens.createToken(app);
        verify(sessionRegistry).add(app);

        app.setExpireTime(System.currentTimeMillis() + 60_000);
        tokens.verifyToken(app);
        verify(sessionRegistry, times(2)).add(app);

        tokens.delLoginUser(app.getToken());
        verify(sessionRegistry).remove(app);
    }

    @Test
    void adminFilterRejectsAppTokenBeforeController() throws Exception
    {
        String jwt = tokens.createToken(user(8, "app"));
        JwtAuthenticationTokenFilter filter = new JwtAuthenticationTokenFilter();
        ReflectionTestUtils.setField(filter, "tokenService", tokens);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/getInfo");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] reached = {false};
        filter.doFilter(request, response, (req, res) -> reached[0] = true);
        assertEquals(403, response.getStatus());
        assertFalse(reached[0]);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
