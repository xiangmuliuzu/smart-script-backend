package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppRefreshSession;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppRefreshSessionMapper;
import com.smartscript.platform.user.security.AppAccessTokenService;
import com.smartscript.platform.user.security.AppAccessTokenService.IssuedAccessToken;

/**
 * H-04（验收标准 §5.3/§8.3）：RefreshSessionService.rotate 状态机分支单测。
 *
 * 覆盖黑盒矩阵（h-a-backend-matrix S4.3 / h-a-h12-stability S12）难以强制触发的分支：
 *   - 过期令牌拒绝且**不**触发家族吊销/审计（防过度吊销）；
 *   - revokeById 竞态丢失（并发第二次轮换）→ REPLAY + 家族吊销 + 安全审计；
 *   - 后继会话 DuplicateKeyException → REPLAY + 家族吊销 + 安全审计；
 *   - 成功路径不变式：后继与会话同 family、markReplaced 绑定前后代、deviceId 截断 64。
 *
 * 重放分支（revokedAt != null）已由 AppLoginAuditEventTest 覆盖，不重复。
 */
class RefreshSessionServiceRotateBranchTest
{
    private static final String RAW = "raw-refresh-token";
    private static final String HASH = com.smartscript.platform.user.util.AppHashes.sha256Hex(RAW);
    private static final String IP = "127.0.0.1";

    private AppRefreshSessionMapper mapper;
    private AppAccessTokenService accessTokenService;
    private AppSessionRevocationService revocationService;
    private AppSecurityEventRecorder securityEvents;
    private RefreshSessionService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(AppRefreshSessionMapper.class);
        accessTokenService = mock(AppAccessTokenService.class);
        revocationService = mock(AppSessionRevocationService.class);
        securityEvents = mock(AppSecurityEventRecorder.class);
        service = new RefreshSessionService(new AppAuthProperties(), mapper,
                accessTokenService, revocationService, securityEvents);
        lenient().when(accessTokenService.issue(anyLong(), anyLong()))
                .thenReturn(new IssuedAccessToken("acc", "jti", 2L, 900L));
    }

    private AppRefreshSession activeSession(long id, String familyId)
    {
        AppRefreshSession s = new AppRefreshSession();
        s.setId(id);
        s.setUserId(1001L);
        s.setFamilyId(familyId);
        s.setDeviceId("dev-64");
        s.setDeviceName("name-64");
        s.setExpiresAt(new Date(System.currentTimeMillis() + 60_000L));
        return s;
    }

    @Test
    void unknownTokenRejectedWithoutAnyRevocation()
    {
        when(mapper.selectByTokenHashForUpdate(HASH)).thenReturn(null);
        when(mapper.selectByTokenHash(HASH)).thenReturn(null);

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service.rotate(RAW, "d", "n", IP));
        assertEquals(AppAuthErrorCodes.REFRESH_INVALID, ex.getCode());
        assertEquals(401, ex.getHttpStatus());

        verify(mapper, never()).revokeFamily(anyString(), anyString(), any(Date.class));
        verify(revocationService, never()).revokeAllForUser(anyLong(), anyString());
        verify(securityEvents, never()).record(anyString(), anyString(), anyString(), anyString());
        verify(mapper, never()).insertSession(any(AppRefreshSession.class));
    }

    @Test
    void expiredTokenRejectedWithoutRevocation()
    {
        AppRefreshSession expired = activeSession(11L, "family-x");
        expired.setExpiresAt(new Date(System.currentTimeMillis() - 1000L));
        when(mapper.selectByTokenHashForUpdate(HASH)).thenReturn(expired);

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service.rotate(RAW, "d", "n", IP));
        assertEquals(AppAuthErrorCodes.REFRESH_INVALID, ex.getCode());

        verify(mapper, never()).revokeFamily(anyString(), anyString(), any(Date.class));
        verify(revocationService, never()).revokeAllForUser(anyLong(), anyString());
        verify(securityEvents, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void lostRevokeRaceTreatedAsReplayAndRevokesFamily()
    {
        AppRefreshSession active = activeSession(12L, "family-r");
        when(mapper.selectByTokenHashForUpdate(HASH)).thenReturn(active);
        when(mapper.revokeById(eq(12L), eq("ROTATED"), any(Date.class))).thenReturn(0);

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service.rotate(RAW, "d", "n", IP));
        assertEquals(AppAuthErrorCodes.REFRESH_REPLAY, ex.getCode());

        verify(mapper).revokeFamily(eq("family-r"), eq("REPLAY"), any(Date.class));
        verify(securityEvents).record(eq("user-1001"), eq(IP), eq("APP_REFRESH_REPLAY"),
                anyString());
        verify(mapper, never()).insertSession(any(AppRefreshSession.class));
    }

    @Test
    void duplicateSuccessorInsertTreatedAsReplayAndRevokesFamily()
    {
        AppRefreshSession active = activeSession(13L, "family-d");
        when(mapper.selectByTokenHashForUpdate(HASH)).thenReturn(active);
        when(mapper.revokeById(eq(13L), eq("ROTATED"), any(Date.class))).thenReturn(1);
        when(mapper.insertSession(any(AppRefreshSession.class)))
                .thenThrow(new DuplicateKeyException("uk_token_hash"));

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service.rotate(RAW, "d", "n", IP));
        assertEquals(AppAuthErrorCodes.REFRESH_REPLAY, ex.getCode());

        verify(mapper).revokeFamily(eq("family-d"), eq("REPLAY"), any(Date.class));
        verify(securityEvents).record(eq("user-1001"), eq(IP), eq("APP_REFRESH_REPLAY"), anyString());
    }

    @Test
    void successRotatesWithinSameFamilyAndLinksReplaced()
    {
        AppRefreshSession active = activeSession(14L, "family-ok");
        String longDevice = "d".repeat(80);
        when(mapper.selectByTokenHashForUpdate(HASH)).thenReturn(active);
        when(mapper.revokeById(eq(14L), eq("ROTATED"), any(Date.class))).thenReturn(1);
        when(mapper.insertSession(any(AppRefreshSession.class))).thenReturn(1);

        RefreshSessionService.IssuedSession issued =
                service.rotate(RAW, longDevice, "n", IP);

        assertNotNull(issued.getRawRefreshToken());
        assertNotEquals(RAW, issued.getRawRefreshToken());
        assertEquals("family-ok", issued.getSession().getFamilyId());
        assertEquals(64, issued.getSession().getDeviceId().length(), "deviceId 超长须截断为 64");
        verify(mapper).revokeById(eq(14L), eq("ROTATED"), any(Date.class));
        verify(mapper).markReplaced(eq(14L), eq(issued.getSession().getId()));
        verify(mapper, never()).revokeFamily(anyString(), anyString(), any(Date.class));
        verify(securityEvents, never()).record(anyString(), anyString(), anyString(), anyString());

        ArgumentCaptor<AppRefreshSession> captor = ArgumentCaptor.forClass(AppRefreshSession.class);
        verify(mapper).insertSession(captor.capture());
        assertEquals("family-ok", captor.getValue().getFamilyId(), "后继必须沿用同一 token family");
        assertTrue(captor.getValue().getTokenHash() != null
                && !captor.getValue().getTokenHash().equals(HASH), "后继哈希必须为新令牌哈希");
    }
}
