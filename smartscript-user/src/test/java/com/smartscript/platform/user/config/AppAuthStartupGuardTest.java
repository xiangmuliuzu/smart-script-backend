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
        p.setRedisKeyPrefix("smartscript:local:app-auth:");
        AppAuthStartupGuard g = new AppAuthStartupGuard(p);
        IllegalStateException ex = assertThrows(IllegalStateException.class, g::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("TOKEN_SECRET"));
    }

    @Test
    void rejectsMockSmsOutsideLocalTest()
    {
        AppAuthProperties p = new AppAuthProperties();
        p.setEnv("prod");
        p.setRedisKeyPrefix("smartscript:prod:app-auth:");
        p.getSms().setProvider("mock");
        AppAuthStartupGuard g = new AppAuthStartupGuard(p);
        setField(g, "pcTokenSecret", "RuoYi-production-test-secret-0123456789");
        IllegalStateException ex = assertThrows(IllegalStateException.class, g::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("mock"));
    }

    @Test
    void acceptsSharedRuoYiSecretWithoutLegacyAppSecret()
    {
        AppAuthProperties p = new AppAuthProperties();
        p.setEnv("local");
        String secret = "A3-local-token-secret-not-weak-0123456789";
        p.setRedisKeyPrefix("smartscript:local:app-auth:");
        p.getSms().setProvider("mock");
        AppAuthStartupGuard g = new AppAuthStartupGuard(p);
        setField(g, "pcTokenSecret", secret);
        g.afterPropertiesSet();
    }

    @Test
    void acceptsLocalMockWithRuoYiSecret()
    {
        AppAuthProperties p = new AppAuthProperties();
        p.setEnv("local");
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
