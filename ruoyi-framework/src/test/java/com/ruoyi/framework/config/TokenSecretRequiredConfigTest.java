package com.ruoyi.framework.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * A0-R1：Token 密钥安全门禁单测（不依赖外部 DB/Redis）。
 */
class TokenSecretRequiredConfigTest
{
    @Test
    void rejectsBlankSecret()
    {
        TokenSecretRequiredConfig config = new TokenSecretRequiredConfig();
        setSecret(config, "   ");
        IllegalStateException ex = assertThrows(IllegalStateException.class, config::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("TOKEN_SECRET is required"));
    }

    @Test
    void rejectsShortSecret()
    {
        TokenSecretRequiredConfig config = new TokenSecretRequiredConfig();
        setSecret(config, "too-short-secret");
        IllegalStateException ex = assertThrows(IllegalStateException.class, config::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("at least"));
    }

    @Test
    void rejectsKnownWeakDefaultSecrets()
    {
        String[] weak = new String[] {
                "abcdefghijklmnopqrstuvwxyz",
                "dev-only-ruoyi-pc-token-secret-change-in-prod-32b",
                "a0r1-dev-token-secret-please-change-in-production-32b",
                "a1-dev-token-secret-please-change-in-production-32bytes"
        };
        for (String s : weak)
        {
            TokenSecretRequiredConfig config = new TokenSecretRequiredConfig();
            setSecret(config, s);
            assertThrows(IllegalStateException.class, config::afterPropertiesSet, s);
        }
    }

    @Test
    void acceptsStrongRandomSecret()
    {
        TokenSecretRequiredConfig config = new TokenSecretRequiredConfig();
        setSecret(config, "R9xK2mQ7vL4nH8pW1sT6yB3cF5jD0aZuE");
        config.afterPropertiesSet();
        assertFalse(TokenSecretRequiredConfig.isWellKnownWeakSecret("R9xK2mQ7vL4nH8pW1sT6yB3cF5jD0aZuE"));
    }

    private static void setSecret(TokenSecretRequiredConfig config, String value)
    {
        try
        {
            java.lang.reflect.Field f = TokenSecretRequiredConfig.class.getDeclaredField("tokenSecret");
            f.setAccessible(true);
            f.set(config, value);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }
}
