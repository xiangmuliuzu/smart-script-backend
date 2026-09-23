package com.smartscript.platform.user.service;

import java.util.Date;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppRefreshSession;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppRefreshSessionMapper;
import com.smartscript.platform.user.security.AppAccessTokenService;
import com.smartscript.platform.user.security.AppAccessTokenService.IssuedAccessToken;
import com.smartscript.platform.user.util.AppHashes;

/**
 * Refresh Token persistence + family rotation + replay detection.
 * Opaque random token; DB stores SHA-256 hash only.
 */
@Service
public class RefreshSessionService
{
    private final AppAuthProperties properties;
    private final AppRefreshSessionMapper mapper;
    private final AppAccessTokenService accessTokenService;
    private final AppSessionRevocationService revocationService;

    public RefreshSessionService(AppAuthProperties properties,
            AppRefreshSessionMapper mapper,
            AppAccessTokenService accessTokenService,
            AppSessionRevocationService revocationService)
    {
        this.properties = properties;
        this.mapper = mapper;
        this.accessTokenService = accessTokenService;
        this.revocationService = revocationService;
    }

    @Transactional
    public IssuedSession createSession(Long userId, String deviceId, String deviceName)
    {
        String raw = AppHashes.randomToken(32);
        String hash = AppHashes.sha256Hex(raw);
        String familyId = UUID.randomUUID().toString().replace("-", "");
        Date expiresAt = new Date(System.currentTimeMillis() + properties.getRefreshTokenTtlSeconds() * 1000L);
        AppRefreshSession session = new AppRefreshSession();
        session.setUserId(userId);
        session.setTokenHash(hash);
        session.setFamilyId(familyId);
        session.setDeviceId(truncate(deviceId, 64));
        session.setDeviceName(truncate(deviceName, 64));
        session.setExpiresAt(expiresAt);
        mapper.insertSession(session);
        IssuedAccessToken access = accessTokenService.issue(userId, session.getId());
        return new IssuedSession(session, raw, access, properties.getRefreshTokenTtlSeconds());
    }

    /**
     * Rotate an unrevoked refresh token. Replay of a rotated token revokes the family.
     * Family revocation must persist even when the call ends with a business error.
     */
    @Transactional(noRollbackFor = AppAuthException.class)
    public IssuedSession rotate(String refreshToken, String deviceId, String deviceName)
    {
        String hash = AppHashes.sha256Hex(refreshToken);
        AppRefreshSession locked;
        try
        {
            locked = mapper.selectByTokenHashForUpdate(hash);
        }
        catch (Exception e)
        {
            // mapper/xml may not lock on non-tx path; fall back to plain select
            locked = mapper.selectByTokenHash(hash);
        }
        if (locked == null)
        {
            throw new AppAuthException(AppAuthErrorCodes.REFRESH_INVALID, 401, "refresh token invalid");
        }
        if (locked.getRevokedAt() != null)
        {
            mapper.revokeFamily(locked.getFamilyId(), "REPLAY", new Date());
            revocationService.revokeAllForUser(locked.getUserId(), "REPLAY");
            // more precise: revoke family sessions only
            throw new AppAuthException(AppAuthErrorCodes.REFRESH_REPLAY, 401, "refresh token replay detected");
        }
        if (locked.getExpiresAt() == null || locked.getExpiresAt().before(new Date()))
        {
            throw new AppAuthException(AppAuthErrorCodes.REFRESH_INVALID, 401, "refresh token expired");
        }

        int revoked = mapper.revokeById(locked.getId(), "ROTATED", new Date());
        if (revoked == 0)
        {
            mapper.revokeFamily(locked.getFamilyId(), "REPLAY", new Date());
            throw new AppAuthException(AppAuthErrorCodes.REFRESH_REPLAY, 401, "refresh token replay detected");
        }

        String raw = AppHashes.randomToken(32);
        String newHash = AppHashes.sha256Hex(raw);
        Date expiresAt = new Date(System.currentTimeMillis() + properties.getRefreshTokenTtlSeconds() * 1000L);
        AppRefreshSession successor = new AppRefreshSession();
        successor.setUserId(locked.getUserId());
        successor.setTokenHash(newHash);
        successor.setFamilyId(locked.getFamilyId());
        successor.setDeviceId(truncate(deviceId != null ? deviceId : locked.getDeviceId(), 64));
        successor.setDeviceName(truncate(deviceName != null ? deviceName : locked.getDeviceName(), 64));
        successor.setExpiresAt(expiresAt);
        try
        {
            mapper.insertSession(successor);
        }
        catch (DuplicateKeyException e)
        {
            mapper.revokeFamily(locked.getFamilyId(), "REPLAY", new Date());
            throw new AppAuthException(AppAuthErrorCodes.REFRESH_REPLAY, 401, "refresh token replay detected");
        }
        mapper.markReplaced(locked.getId(), successor.getId());
        IssuedAccessToken access = accessTokenService.issue(locked.getUserId(), successor.getId());
        return new IssuedSession(successor, raw, access, properties.getRefreshTokenTtlSeconds());
    }

    public AppRefreshSession findActive(String refreshToken)
    {
        String hash = AppHashes.sha256Hex(refreshToken);
        AppRefreshSession session = mapper.selectByTokenHash(hash);
        if (session == null)
        {
            throw new AppAuthException(AppAuthErrorCodes.REFRESH_INVALID, 401, "refresh token invalid");
        }
        if (session.getRevokedAt() != null)
        {
            throw new AppAuthException(AppAuthErrorCodes.REFRESH_INVALID, 401, "refresh token revoked");
        }
        if (session.getExpiresAt() == null || session.getExpiresAt().before(new Date()))
        {
            throw new AppAuthException(AppAuthErrorCodes.REFRESH_INVALID, 401, "refresh token expired");
        }
        return session;
    }

    private static String truncate(String s, int max)
    {
        if (s == null)
        {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static class IssuedSession
    {
        private final AppRefreshSession session;
        private final String rawRefreshToken;
        private final IssuedAccessToken accessToken;
        private final long refreshExpiresIn;

        public IssuedSession(AppRefreshSession session, String rawRefreshToken,
                IssuedAccessToken accessToken, long refreshExpiresIn)
        {
            this.session = session;
            this.rawRefreshToken = rawRefreshToken;
            this.accessToken = accessToken;
            this.refreshExpiresIn = refreshExpiresIn;
        }

        public AppRefreshSession getSession()
        {
            return session;
        }

        public String getRawRefreshToken()
        {
            return rawRefreshToken;
        }

        public IssuedAccessToken getAccessToken()
        {
            return accessToken;
        }

        public long getRefreshExpiresIn()
        {
            return refreshExpiresIn;
        }

        public Long getSessionId()
        {
            return session.getId();
        }

        public Long getUserId()
        {
            return session.getUserId();
        }
    }
}
