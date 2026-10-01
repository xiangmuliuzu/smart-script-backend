package com.smartscript.platform.user.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.service.AppSessionRevocationService;
import com.smartscript.platform.user.util.AppHashes;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * App Access Token issuer/validator. Isolated from PC RuoYi token domain
 * via secret, issuer, audience and token_type claim.
 */
@Component
public class AppAccessTokenService
{
    public static final String TOKEN_TYPE = "app_access";
    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_SESSION_ID = "sid";
    /** Single allowed signing algorithm; algorithm negotiation is rejected. */
    public static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS512;

    private final AppAuthProperties properties;
    private final AppSessionRevocationService revocationService;
    private final AppUserMapper appUserMapper;

    public AppAccessTokenService(AppAuthProperties properties,
            AppSessionRevocationService revocationService,
            AppUserMapper appUserMapper)
    {
        this.properties = properties;
        this.revocationService = revocationService;
        this.appUserMapper = appUserMapper;
    }

    public IssuedAccessToken issue(Long userId, Long sessionId)
    {
        long now = System.currentTimeMillis();
        long expMs = now + properties.getAccessTokenTtlSeconds() * 1000L;
        String jti = AppHashes.randomToken(16);
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE);
        claims.put(CLAIM_SESSION_ID, sessionId);
        claims.put("sub", String.valueOf(userId));
        claims.put("iss", properties.getTokenIssuer());
        claims.put("aud", properties.getTokenAudience());
        claims.put("iat", new Date(now));
        claims.put("exp", new Date(expMs));
        claims.put("jti", jti);
        // jjwt 0.9.1 setClaims overwrites; re-apply required standard fields after
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuer(properties.getTokenIssuer())
                .setAudience(properties.getTokenAudience())
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(expMs))
                .setId(jti)
                .signWith(SignatureAlgorithm.HS512, secretBytes())
                .compact();
        return new IssuedAccessToken(token, jti, sessionId, properties.getAccessTokenTtlSeconds());
    }

    public AppIdentityContext parseAndValidate(String token)
    {
        if (token == null || token.isBlank())
        {
            throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "unauthorized");
        }
        Claims claims;
        try
        {
            // jjwt 0.9.1 chooses the verification algorithm from the token header, so a
            // token signed with the same secret but a different algorithm (e.g. HS256)
            // would otherwise validate. The header algorithm is pinned before use.
            Jws<Claims> jws = Jwts.parser()
                    .setSigningKey(secretBytes())
                    .parseClaimsJws(token);
            if (!SIGNATURE_ALGORITHM.getValue().equals(jws.getHeader().getAlgorithm()))
            {
                throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "unexpected token algorithm");
            }
            claims = jws.getBody();
        }
        catch (AppAuthException e)
        {
            throw e;
        }
        catch (ExpiredJwtException e)
        {
            throw new AppAuthException(AppAuthErrorCodes.ACCESS_EXPIRED, 401, "access token expired");
        }
        catch (JwtException | IllegalArgumentException e)
        {
            throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "unauthorized");
        }

        String tokenType = String.valueOf(claims.get(CLAIM_TOKEN_TYPE));
        if (!TOKEN_TYPE.equals(tokenType))
        {
            throw new AppAuthException(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, 403, "credential domain mismatch");
        }
        if (!properties.getTokenIssuer().equals(claims.getIssuer())
                || claims.getAudience() == null
                || !properties.getTokenAudience().equals(claims.getAudience()))
        {
            throw new AppAuthException(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, 403, "credential domain mismatch");
        }

        Date exp = claims.getExpiration();
        if (exp == null || exp.before(new Date()))
        {
            throw new AppAuthException(AppAuthErrorCodes.ACCESS_EXPIRED, 401, "access token expired");
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank())
        {
            throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "unauthorized");
        }
        Long userId = Long.valueOf(sub);
        String jti = claims.getId();
        Object sidObj = claims.get(CLAIM_SESSION_ID);
        Long sessionId = sidObj == null ? null : Long.valueOf(String.valueOf(sidObj));

        if (sessionId != null && revocationService.isSessionRevoked(sessionId))
        {
            throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "session revoked");
        }
        if (jti != null && revocationService.isJtiRevoked(jti))
        {
            throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "session revoked");
        }

        var user = appUserMapper.selectById(userId);
        if (user == null || !user.isUsable())
        {
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "account disabled");
        }

        return new AppIdentityContext(
                user.getUserId(),
                user.getUserType(),
                user.getStatus(),
                AppHashes.maskPhone(user.getPhonenumber()),
                user.getNickName(),
                emptyToNull(user.getAvatar()),
                mapRealName(user.getUserId()),
                sessionId,
                jti,
                null);
    }

    private String mapRealName(Long userId)
    {
        try
        {
            String status = appUserMapper.selectRealNameStatus(userId);
            return status == null || status.isBlank() ? "NOT_SUBMITTED" : status;
        }
        catch (Exception e)
        {
            return "NOT_SUBMITTED";
        }
    }

    private static String emptyToNull(String s)
    {
        return s == null || s.isBlank() ? null : s;
    }

    private byte[] secretBytes()
    {
        return properties.getTokenSecret().getBytes(StandardCharsets.UTF_8);
    }

    public static class IssuedAccessToken
    {
        private final String token;
        private final String jti;
        private final Long sessionId;
        private final long expiresIn;

        public IssuedAccessToken(String token, String jti, Long sessionId, long expiresIn)
        {
            this.token = token;
            this.jti = jti;
            this.sessionId = sessionId;
            this.expiresIn = expiresIn;
        }

        public String getToken()
        {
            return token;
        }

        public String getJti()
        {
            return jti;
        }

        public Long getSessionId()
        {
            return sessionId;
        }

        public long getExpiresIn()
        {
            return expiresIn;
        }
    }

}
