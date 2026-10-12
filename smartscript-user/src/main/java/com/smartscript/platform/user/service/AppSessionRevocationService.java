package com.smartscript.platform.user.service;

import org.springframework.stereotype.Service;
import com.ruoyi.framework.web.service.TokenService;
import com.smartscript.platform.user.config.AppAuthProperties;

/** Revokes App sessions in the shared RuoYi cache, scoped to the App domain. */
@Service
public class AppSessionRevocationService
{
    private final TokenService tokenService;
    private final AppAuthProperties properties;

    public AppSessionRevocationService(AppAuthProperties properties, TokenService tokenService)
    {
        this.properties = properties;
        this.tokenService = tokenService;
    }

    /** Shared namespace used by SMS, phone-change and feedback rate limits. */
    public String key(String suffix)
    {
        String prefix = properties.getRedisKeyPrefix();
        return (prefix.endsWith(":") ? prefix : prefix + ":") + suffix;
    }

    public void revokeAllForUser(Long userId, String reason)
    {
        tokenService.revokeUserSessions(userId, "app", null);
    }

    public void revokeSession(String token, String reason)
    {
        if (token != null && !token.isBlank())
        {
            tokenService.delLoginUser(token);
        }
    }

    public void revokeOthersForUser(Long userId, String keepToken, String reason)
    {
        tokenService.revokeUserSessions(userId, "app", keepToken);
    }
}
