package com.ruoyi.framework.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * A0-R1 安全门禁：PC 管理员 Token 密钥必须来自环境变量。
 * 缺失或过短时拒绝启动，禁止回退到仓库内默认密钥。
 */
@Configuration
public class TokenSecretRequiredConfig implements InitializingBean
{
    public static final int MIN_SECRET_LENGTH = 32;

    @Value("${token.secret:}")
    private String tokenSecret;

    @Override
    public void afterPropertiesSet()
    {
        if (!hasText(tokenSecret))
        {
            throw new IllegalStateException(
                    "TOKEN_SECRET is required. Refusing to start with empty/default JWT secret (A0-R1 security gate).");
        }
        String trimmed = tokenSecret.trim();
        if (trimmed.length() < MIN_SECRET_LENGTH)
        {
            throw new IllegalStateException(
                    "TOKEN_SECRET must be at least " + MIN_SECRET_LENGTH + " characters (A0-R1 security gate).");
        }
        if (isWellKnownWeakSecret(trimmed))
        {
            throw new IllegalStateException(
                    "TOKEN_SECRET matches a known weak/default value and must be rotated (A0-R1 security gate).");
        }
    }

    static boolean isWellKnownWeakSecret(String secret)
    {
        String s = secret.toLowerCase();
        return s.contains("dev-only")
                || s.contains("please-change")
                || s.equals("abcdefghijklmnopqrstuvwxyz")
                || s.contains("a0r1-dev-token")
                || s.contains("a1-dev-token");
    }

    private static boolean hasText(String s)
    {
        return s != null && !s.trim().isEmpty();
    }
}
