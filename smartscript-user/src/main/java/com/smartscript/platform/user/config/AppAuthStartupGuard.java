package com.smartscript.platform.user.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A3 security gates:
 * - The shared RuoYi TOKEN_SECRET must be strong.
 * - Mock SMS only allowed in local/test profiles.
 */
@Component
public class AppAuthStartupGuard implements InitializingBean
{
    public static final int MIN_SECRET_LENGTH = 32;

    private final AppAuthProperties properties;

    @Value("${token.secret:}")
    private String pcTokenSecret;

    public AppAuthStartupGuard(AppAuthProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet()
    {
        String secret = pcTokenSecret;
        if (!hasText(secret))
        {
            throw new IllegalStateException(
                    "TOKEN_SECRET is required for RuoYi sessions.");
        }
        String trimmed = secret.trim();
        if (trimmed.length() < MIN_SECRET_LENGTH)
        {
            throw new IllegalStateException(
                    "TOKEN_SECRET must be at least " + MIN_SECRET_LENGTH + " characters.");
        }
        if (isWeak(trimmed))
        {
            throw new IllegalStateException(
                    "TOKEN_SECRET matches a known weak/default value and must be rotated.");
        }
        if (!hasText(properties.getRedisKeyPrefix()))
        {
            throw new IllegalStateException(
                    "APP_REDIS_KEY_PREFIX is required, e.g. smartscript:dev:app-auth: (A3 security gate).");
        }
        String provider = properties.getSms().getProvider() == null ? "" : properties.getSms().getProvider().trim();
        if ("mock".equalsIgnoreCase(provider) && !properties.isLocalOrTestProfile())
        {
            throw new IllegalStateException(
                    "SMS_PROVIDER=mock is only allowed in local/test profiles. Refusing to start (A3 security gate).");
        }
    }

    static boolean isWeak(String secret)
    {
        String s = secret.toLowerCase();
        return s.contains("dev-only")
                || s.contains("please-change")
                || s.contains("a3-dev-token")
                || s.contains("app-token-secret")
                || s.equals("abcdefghijklmnopqrstuvwxyz");
    }

    private static boolean hasText(String s)
    {
        return s != null && !s.trim().isEmpty();
    }
}
