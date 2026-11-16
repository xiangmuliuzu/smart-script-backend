package com.smartscript.platform.user.service;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.ruoyi.framework.web.service.TokenService;
import com.smartscript.platform.user.config.AppAuthProperties;

class AppRuoYiRevocationTest
{
    @Test
    void logoutPasswordSetAndAccountRevocationReachRuoYiCache()
    {
        TokenService tokens = mock(TokenService.class);
        var service = new AppSessionRevocationService(new AppAuthProperties(), tokens);
        service.revokeSession("current-session", "LOGOUT");
        verify(tokens).delLoginUser("current-session");
        service.revokeOthersForUser(8L, "keep-session", "PASSWORD_SET");
        verify(tokens).revokeUserSessions(8L, "app", "keep-session");
        service.revokeAllForUser(8L, "PASSWORD_CHANGE");
        verify(tokens).revokeUserSessions(8L, "app", null);
    }
}
