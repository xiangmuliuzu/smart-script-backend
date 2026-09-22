package com.smartscript.platform.user.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * A5 回归：App 凭证域的身份判定必须覆盖用户中心全部端点。
 *
 * 背景：A3 的过滤器按「私有路径白名单」判定，A5 新增 /api/v1/users、/api/v1/messages、
 * /api/v1/feedback 后未同步，导致这些接口在过滤器里被当作无需身份，最终被
 * authorizeHttpRequests 的 authenticated() 拒绝（401 且无业务语义）。
 * 本测试锁定「公开路径之外一律需要身份」的口径，避免同类漏配再次发生。
 */
class AppAuthIdentityScopeTest
{
    @Test
    void a5UserCenterPathsRequireAppIdentity()
    {
        String[] protectedPaths = {
                "/api/v1/users/me/profile",
                "/api/v1/users/me/real-name",
                "/api/v1/users/me/real-name/resubmit",
                "/api/v1/users/me/phone/change/old/send",
                "/api/v1/users/me/phone/change/old/verify",
                "/api/v1/users/me/phone/change/new/send",
                "/api/v1/users/me/phone/change/confirm",
                "/api/v1/users/me/notification-preferences",
                "/api/v1/messages",
                "/api/v1/messages/unread-count",
                "/api/v1/messages/read-all",
                "/api/v1/messages/12",
                "/api/v1/messages/12/read",
                "/api/v1/feedback",
                "/api/v1/feedback/7",
        };
        for (String path : protectedPaths)
        {
            assertTrue(AppAuthAuthenticationFilter.requiresAppIdentity(path),
                    "must require App identity: " + path);
        }
    }

    @Test
    void a3PrivatePathsStillRequireAppIdentity()
    {
        String[] protectedPaths = {
                "/api/v1/auth/logout",
                "/api/v1/auth/me",
                "/api/v1/auth/password/set",
                "/api/v1/auth/password/change",
        };
        for (String path : protectedPaths)
        {
            assertTrue(AppAuthAuthenticationFilter.requiresAppIdentity(path),
                    "must require App identity: " + path);
        }
    }

    @Test
    void publicAuthPathsDoNotRequireAppIdentity()
    {
        String[] publicPaths = {
                "/api/v1/auth/sms/send",
                "/api/v1/auth/sms/login",
                "/api/v1/auth/password/login",
                "/api/v1/auth/password/reset",
                "/api/v1/auth/register",
                "/api/v1/auth/token/refresh",
                "/api/v1/auth/agreements",
                "/api/v1/auth/oauth/wechat/login",
                "/api/v1/auth/oauth/qq/login",
        };
        for (String path : publicPaths)
        {
            assertFalse(AppAuthAuthenticationFilter.requiresAppIdentity(path),
                    "must stay public: " + path);
        }
        assertFalse(AppAuthAuthenticationFilter.requiresAppIdentity(null));
    }
}
