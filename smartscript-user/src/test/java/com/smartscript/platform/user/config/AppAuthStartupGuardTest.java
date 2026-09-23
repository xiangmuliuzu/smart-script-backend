package com.smartscript.platform.user.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AppAuthStartupGuardTest
{
    @Test
    void rejectsEmptySecret()
    {
        AppAuthProperties p = new AppAuthProperties();
        p.setEnv("local");
        p.setTokenIssuer("iss");
        p.setTokenAudience("aud");
        p.setRedisKeyPrefix("smartscript:local:app-auth:");
        AppAuthStartupGuard g = new AppAuthStartupGuard(p);
        IllegalStateException ex = assertThrows(IllegalStateException.class, g::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("APP_TOKEN_SECRET"));
    }

    @Test
    void rejectsMockSmsOutsideLocalTest()
    {
        AppAuthProperties p = new AppAuthProperties();
        p.setEnv("prod");
        p.setTokenSecret("A3-prod-token-secret-not-weak-0123456789");
        p.setTokenIssuer("iss");
        p.setTokenAudience("aud");
        p.setRedisKeyPrefix("smartscript:prod:app-auth:");
        p.getSms().setProvider("mock");
        AppAuthStartupGuard g = new AppAuthStartupGuard(p);
        IllegalStateException ex = assertThrows(IllegalStateException.class, g::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("mock"));
    }

    @Test
    void rejectsSameSecretAsPc()
    {
        AppAuthProperties p = new AppAuthProperties();
        p.setEnv("local");
        String secret = "A3-local-token-secret-not-weak-0123456789";
        p.setTokenSecret(secret);
        p.setTokenIssuer("iss");
        p.setTokenAudience("aud");
        p.setRedisKeyPrefix("smartscript:local:app-auth:");
        p.getSms().setProvider("mock");
        AppAuthStartupGuard g = new AppAuthStartupGuard(p);
        setField(g, "pcTokenSecret", secret);
        IllegalStateException ex = assertThrows(IllegalStateException.class, g::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("must differ"));
    }

    @Test
    void acceptsLocalMockWithDistinctSecret()
    {
        AppAuthProperties p = new AppAuthProperties();
        p.setEnv("local");
        p.setTokenSecret("A3-local-token-secret-not-weak-0123456789");
        p.setTokenIssuer("smartscript-app");
        p.setTokenAudience("smartscript-app-client");
        p.setRedisKeyPrefix("smartscript:local:app-auth:");
        p.getSms().setProvider("mock");
        AppAuthStartupGuard g = new AppAuthStartupGuard(p);
        setField(g, "pcTokenSecret", "A3-pc-token-secret-different-abcdef0123456789");
        g.afterPropertiesSet();
    }

    private static void setField(Object target, String name, Object value)
    {
        try
        {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }
}
