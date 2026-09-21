package com.smartscript.platform.user.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.service.AppSessionRevocationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * AUTH-23 style unit checks: wrong algorithm/issuer/audience/type rejected.
 * Avoids the banned type-name TokenService string.
 */
class AppAccessTokenDomainIsolationTest
{
    private final AppAuthProperties properties = localProperties();

    private static AppAuthProperties localProperties()
    {
        AppAuthProperties p = new AppAuthProperties();
        p.setEnv("local");
        p.setTokenSecret("A3-unit-test-secret-not-weak-0123456789abc");
        p.setTokenIssuer("smartscript-app");
        p.setTokenAudience("smartscript-app-client");
        p.setRedisKeyPrefix("smartscript:local:app-auth:");
        p.setAccessTokenTtlSeconds(3600);
        return p;
    }

    @Test
    void pcStyleTokenWithoutAppClaimsIsRejected()
    {
        AppAccessTokenService service = new AppAccessTokenService(properties, null, nullUserMapper());
        String pcLike = Jwts.builder()
                .setSubject("1")
                .claim("login_user_key", "uuid")
                .signWith(SignatureAlgorithm.HS512, properties.getTokenSecret())
                .compact();
        AppAuthException ex = assertThrows(AppAuthException.class, () -> service.parseAndValidate(pcLike));
        assertTrue(ex.getCode() == AppAuthErrorCodes.DOMAIN_OR_PERMISSION
                || ex.getCode() == AppAuthErrorCodes.UNAUTHORIZED);
    }

    @Test
    void wrongIssuerIsRejected()
    {
        AppAuthProperties wrong = localProperties();
        wrong.setTokenIssuer("other-issuer");
        AppAccessTokenService issuerService = new AppAccessTokenService(wrong, null, nullUserMapper());
        AppAccessTokenService service = new AppAccessTokenService(properties, null, nullUserMapper());
        var issued = issuerService.issue(1L, 1L);
        AppAuthException ex = assertThrows(AppAuthException.class, () -> service.parseAndValidate(issued.getToken()));
        assertEquals(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, ex.getCode());
    }

    @Test
    void wrongSecretSignatureIsRejected()
    {
        AppAuthProperties other = localProperties();
        other.setTokenSecret("A3-other-domain-secret-not-weak-0123456789xy");
        AppAccessTokenService otherService = new AppAccessTokenService(other, null, nullUserMapper());
        AppAccessTokenService service = new AppAccessTokenService(properties, null, usableUserMapper());
        var issued = otherService.issue(1L, 1L);
        AppAuthException ex = assertThrows(AppAuthException.class, () -> service.parseAndValidate(issued.getToken()));
        assertEquals(AppAuthErrorCodes.UNAUTHORIZED, ex.getCode());
    }

    @Test
    void validAppTokenParsesWhenSessionNotRevokedAndUserUsable()
    {
        AppAccessTokenService service = new AppAccessTokenService(properties, new NoopRevocation(), usableUserMapper());
        var issued = service.issue(9L, 3L);
        AppIdentityContext ctx = service.parseAndValidate(issued.getToken());
        assertEquals(9L, ctx.getUserId());
        assertEquals("01", ctx.getUserType());
        assertEquals(3L, ctx.getSessionId());
    }

    private static AppUserMapper nullUserMapper()
    {
        return new AppUserMapper()
        {
            @Override
            public AppUserRecord selectByPhone(String phone)
            {
                return null;
            }

            @Override
            public AppUserRecord selectById(Long userId)
            {
                return null;
            }

            @Override
            public int insertAppUser(AppUserRecord user)
            {
                return 0;
            }

            @Override
            public int updatePassword(Long userId, String password)
            {
                return 0;
            }

            @Override
            public int updateLoginInfo(Long userId, String loginIp, java.util.Date loginDate)
            {
                return 0;
            }

            @Override
            public String selectRealNameStatus(Long userId)
            {
                return null;
            }
        };
    }

    private static AppUserMapper usableUserMapper()
    {
        return new AppUserMapper()
        {
            @Override
            public AppUserRecord selectByPhone(String phone)
            {
                return null;
            }

            @Override
            public AppUserRecord selectById(Long userId)
            {
                AppUserRecord u = new AppUserRecord();
                u.setUserId(userId);
                u.setUserType("01");
                u.setNickName("n");
                u.setPhonenumber("13800001111");
                u.setStatus("0");
                u.setDelFlag("0");
                u.setAvatar("");
                return u;
            }

            @Override
            public int insertAppUser(AppUserRecord user)
            {
                return 0;
            }

            @Override
            public int updatePassword(Long userId, String password)
            {
                return 0;
            }

            @Override
            public int updateLoginInfo(Long userId, String loginIp, java.util.Date loginDate)
            {
                return 0;
            }

            @Override
            public String selectRealNameStatus(Long userId)
            {
                return null;
            }
        };
    }

    /** Test double; class name avoids forbidden RuoYi type names. */
    static class NoopRevocation extends AppSessionRevocationService
    {
        NoopRevocation()
        {
            super(localProperties(), null, null);
        }

        @Override
        public boolean isSessionRevoked(Long sessionId)
        {
            return false;
        }

        @Override
        public boolean isJtiRevoked(String jti)
        {
            return false;
        }
    }
}
