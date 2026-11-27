package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.ruoyi.common.core.redis.RedisCache;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.dto.AuthSessionDto;
import com.smartscript.platform.user.dto.CurrentUserDto;
import com.smartscript.platform.user.dto.QrLoginTicket;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.security.AppIdentityContext;

/**
 * 扫码登录主链路：出码 → 扫码 → 确认 → 轮询取牌，以及各处的越权 / 重放 / 过期拒绝。
 *
 * <p>用内存 Map 顶替 Redis，只验证状态机与服务层口径；Redis 的原子性由
 * {@code GETDEL}/{@code SET NX} 本身保证，不在这里模拟并发。</p>
 */
class QrLoginFlowTest
{
    private static final String CREATOR_PORTAL = QrLoginTicket.PORTAL_CREATOR;

    private final Map<String, Object> store = new HashMap<>();
    private final ValueOperations<Object, Object> valueOps = mock(ValueOperations.class);
    /** 内存版 RedisCache：覆盖模板方法，绕开真实连接与序列化。 */
    private final RedisCache redis = new RedisCache()
    {
        @Override
        public <T> T getCacheObject(String key)
        {
            return (T) store.get(key);
        }

        @Override
        public <T> void setCacheObject(String key, T value, Integer timeout, TimeUnit timeUnit)
        {
            store.put(key, value);
        }

        @Override
        public long getExpire(String key)
        {
            return store.containsKey(key) ? 60L : -2L;
        }

        @Override
        public boolean deleteObject(String key)
        {
            return store.remove(key) != null;
        }
    };
    private final AppAuthProperties properties = new AppAuthProperties();
    private final AppAuthenticationService authService = mock(AppAuthenticationService.class);
    private QrLoginService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp()
    {
        store.clear();
        // redisTemplate 是 RedisCache 的 public 字段，直接赋值即可；getAndDelete 模拟 GETDEL 的取走语义。
        redis.redisTemplate = mock(RedisTemplate.class);
        when(redis.redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete(anyString())).thenAnswer(i -> store.remove(i.getArgument(0)));
        when(valueOps.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(true);

        service = new QrLoginService(new QrLoginTickets(redis, properties), properties, authService);
    }

    private static AppIdentityContext identity(long userId, String nickName)
    {
        return new AppIdentityContext(userId, "02", "0", "139****0000", nickName, null, "APPROVED", 1L,
                "jti-" + userId, "token");
    }

    private CurrentUserDto creator(long userId, boolean authorCapability)
    {
        CurrentUserDto dto = new CurrentUserDto();
        dto.setUserId(userId);
        dto.setUserType("02");
        dto.setNickname("编剧甲");
        dto.setAuthorCapability(authorCapability);
        return dto;
    }

    private void stubOwner(long userId, boolean authorCapability)
    {
        when(authService.me(any())).thenAnswer(i ->
        {
            AppIdentityContext ctx = i.getArgument(0);
            return creator(ctx.getUserId(), ctx.getUserId() == userId && authorCapability);
        });
        AuthSessionDto session = new AuthSessionDto();
        session.setToken("issued-token");
        session.setUser(creator(userId, authorCapability));
        when(authService.issueQrLoginSession(any(), anyString())).thenReturn(session);
    }

    private String createTicket()
    {
        Map<String, Object> created = service.create(CREATOR_PORTAL, "Chrome 130", "Windows 11");
        assertEquals(QrLoginTicket.STATUS_WAITING, created.get("status"));
        assertEquals(CREATOR_PORTAL, created.get("portal"));
        assertEquals(120, created.get("expiresIn"));
        assertEquals(2, created.get("pollInterval"));
        // 票据明文只出现在响应里，键上不会出现；这里只断言拿得到。
        String token = (String) created.get("qrToken");
        assertNotNull(token);
        assertTrue(token.length() >= 32);
        return token;
    }

    @Test
    void pollBeforeScanReportsWaitingWithoutToken()
    {
        String qr = createTicket();
        Map<String, Object> poll = service.poll(qr, "10.0.0.1");
        assertEquals(QrLoginTicket.STATUS_WAITING, poll.get("status"));
        assertFalse(poll.containsKey("token"));
    }

    @Test
    void scanThenPollReportsScannedWithScannerInfo()
    {
        String qr = createTicket();
        service.scan(identity(7L, "编剧甲"), qr);
        Map<String, Object> poll = service.poll(qr, "10.0.0.1");
        assertEquals(QrLoginTicket.STATUS_SCANNED, poll.get("status"));
        assertEquals("编剧甲", poll.get("nickname"));
        assertEquals("139****0000", poll.get("phoneMasked"));
        assertEquals("Chrome 130", poll.get("browser"));
        assertFalse(poll.containsKey("token"));
    }

    @Test
    void secondScanIsRejected()
    {
        String qr = createTicket();
        service.scan(identity(7L, "编剧甲"), qr);
        AppAuthException error = assertThrows(AppAuthException.class,
                () -> service.scan(identity(8L, "别人"), qr));
        assertEquals(AppAuthErrorCodes.PARAM, error.getCode());
    }

    @Test
    void confirmByAnotherAccountIsRejected()
    {
        String qr = createTicket();
        service.scan(identity(7L, "编剧甲"), qr);
        AppAuthException error = assertThrows(AppAuthException.class,
                () -> service.confirm(identity(8L, "别人"), qr));
        assertEquals(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, error.getCode());
    }

    @Test
    void confirmKeepsTicketScannedWhenCreatorCapabilityMissing()
    {
        String qr = createTicket();
        service.scan(identity(7L, "编剧甲"), qr);
        when(authService.me(any())).thenAnswer(i -> creator(7L, false));

        AppAuthException error = assertThrows(AppAuthException.class,
                () -> service.confirm(identity(7L, "编剧甲"), qr));
        assertEquals(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, error.getCode());
        // 准入失败不得把票据推进到 CONFIRMED，否则 PC 会拿到一张永远签不出令牌的票。
        assertEquals(QrLoginTicket.STATUS_SCANNED, service.poll(qr, "10.0.0.1").get("status"));
    }

    @Test
    void confirmDoesNotIssueTokenUntilPollAndPollIsSingleUse()
    {
        stubOwner(7L, true);
        String qr = createTicket();
        service.scan(identity(7L, "编剧甲"), qr);
        service.confirm(identity(7L, "编剧甲"), qr);

        // 确认阶段只推进状态，令牌在轮询里才签发。
        assertTrue(store.values().stream().noneMatch(v -> String.valueOf(v).contains("issued-token")));

        Map<String, Object> first = service.poll(qr, "10.0.0.1");
        assertEquals(QrLoginTicket.STATUS_CONFIRMED, first.get("status"));
        assertEquals("issued-token", first.get("token"));
        assertNotNull(first.get("user"));

        // 票据已被取走：再轮询一次按失效处理，同一张票不会换出第二个登录态。
        AppAuthException error = assertThrows(AppAuthException.class, () -> service.poll(qr, "10.0.0.2"));
        assertEquals(AppAuthErrorCodes.PARAM, error.getCode());
    }

    @Test
    void confirmBeforeScanIsRejected()
    {
        String qr = createTicket();
        AppAuthException error = assertThrows(AppAuthException.class,
                () -> service.confirm(identity(7L, "编剧甲"), qr));
        assertEquals(AppAuthErrorCodes.PARAM, error.getCode());
    }

    @Test
    void unknownTicketIsTreatedAsExpired()
    {
        AppAuthException error = assertThrows(AppAuthException.class,
                () -> service.poll("not-a-real-ticket", "10.0.0.1"));
        assertEquals(AppAuthErrorCodes.PARAM, error.getCode());
        assertTrue(error.getMessage().contains("二维码已失效"));
    }

    @Test
    void cancelRemovesTicket()
    {
        String qr = createTicket();
        service.scan(identity(7L, "编剧甲"), qr);
        service.cancel(identity(7L, "编剧甲"), qr);
        AppAuthException error = assertThrows(AppAuthException.class, () -> service.poll(qr, "10.0.0.1"));
        assertEquals(AppAuthErrorCodes.PARAM, error.getCode());
    }

    @Test
    void browserCanAbortAScannedTicketWithoutIdentity()
    {
        // 抢扫防护：票据令牌只在出码方手里，因此 PC 凭令牌即可拒绝，无需登录身份。
        String qr = createTicket();
        service.scan(identity(7L, "陌生人"), qr);
        assertEquals(QrLoginTicket.STATUS_CANCELLED, service.abort(qr).get("status"));
        // 票据已作废：轮到谁来都不能再确认或取牌。
        AppAuthException error = assertThrows(AppAuthException.class, () -> service.poll(qr, "10.0.0.1"));
        assertEquals(AppAuthErrorCodes.PARAM, error.getCode());
    }

    @Test
    void enterprisePortalSkipsCreatorCapability()
    {
        when(authService.me(any())).thenAnswer(i -> creator(9L, false));
        Map<String, Object> created = service.create(QrLoginTicket.PORTAL_ENTERPRISE, "Edge 130", "macOS");
        String qr = (String) created.get("qrToken");
        service.scan(identity(9L, "采购甲"), qr);
        // 企业端只要普通用户账号，不要求创作者能力。
        assertEquals(QrLoginTicket.STATUS_CONFIRMED, service.confirm(identity(9L, "采购甲"), qr).get("status"));
    }

    @Test
    void pollRejectsRapidRepeatsForSameTicket()
    {
        String qr = createTicket();
        when(valueOps.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(false);
        AppAuthException error = assertThrows(AppAuthException.class, () -> service.poll(qr, "10.0.0.1"));
        assertEquals(AppAuthErrorCodes.PARAM, error.getCode());
    }

    @Test
    void expiredTicketCannotBeScanned()
    {
        String qr = createTicket();
        store.clear();
        AppAuthException error = assertThrows(AppAuthException.class,
                () -> service.scan(identity(7L, "编剧甲"), qr));
        assertEquals(AppAuthErrorCodes.PARAM, error.getCode());
        assertNull(store.get(qr));
    }
}
