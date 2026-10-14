package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.mapper.AppIdentityMapper;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.security.AppIdentityContext;
import com.smartscript.platform.user.security.AppRuoYiSessionService;

/** 登录入口是匿名请求；返回身份必须对应已验证账号，不能采用游客或别的会话。 */
class AppLoginCreatorCapabilityTest
{
    private final AppUserMapper users = mock(AppUserMapper.class);
    private final AppIdentityMapper identities = mock(AppIdentityMapper.class);
    private final AppRuoYiSessionService sessions = mock(AppRuoYiSessionService.class);
    private AppAuthenticationService service;

    @BeforeEach
    void setup()
    {
        SecurityContextHolder.clearContext();
        AppUserRecord user = new AppUserRecord();
        user.setUserId(7L);
        user.setUserName("creator-test");
        user.setUserType("01");
        user.setStatus("0");
        user.setDelFlag("0");
        when(users.selectByLoginIdentifier("creator-test")).thenReturn(user);
        when(users.selectById(7L)).thenReturn(user);
        when(users.selectRealNameStatus(7L)).thenReturn("APPROVED");
        when(sessions.issue(user)).thenReturn("test-only-token");
        when(identities.selectRoleCodes(7L)).thenReturn(List.of("creator"));
        when(identities.selectPermissionCodes(7L)).thenReturn(List.of("content:write"));
        service = new AppAuthenticationService(users, mock(SmsCodeService.class), mock(AgreementService.class),
                sessions, mock(AppSessionRevocationService.class), new BCryptPasswordEncoder(),
                identities, null);
    }

    @AfterEach
    void cleanup()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousLoginReturnsGrantedCapabilityAndRoles()
    {
        when(identities.selectAuthorCapabilityEnabled(7L)).thenReturn(true);
        var result = service.ruoyiPasswordLogin("creator-test", "test-only-password", null, null, "127.0.0.1");
        assertTrue(result.getUser().isAuthorCapability());
        assertArrayEquals(new String[] { "creator" }, result.getUser().getRoles());
        assertArrayEquals(new String[] { "content:write" }, result.getUser().getPermissions());
        assertEquals("APPROVED", result.getUser().getRealNameStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void grantAndRevocationAreReadAgainOnLoginAndIdentityRestore()
    {
        when(identities.selectAuthorCapabilityEnabled(7L)).thenReturn(false, true, false);
        assertFalse(service.ruoyiPasswordLogin("creator-test", "test-only-password", null, null, "127.0.0.1")
                .getUser().isAuthorCapability());
        assertTrue(service.ruoyiPasswordLogin("creator-test", "test-only-password", null, null, "127.0.0.1")
                .getUser().isAuthorCapability());
        assertFalse(service.me(principal(7L)).isAuthorCapability());
        verify(identities, times(3)).selectAuthorCapabilityEnabled(7L);
    }

    @Test
    void existingPrincipalCannotReplaceCredentialOwnersCapability()
    {
        var other = principal(99L);
        SecurityContextHolder.getContext().setAuthentication(other);
        when(identities.selectAuthorCapabilityEnabled(7L)).thenReturn(true);
        var result = service.ruoyiPasswordLogin("creator-test", "test-only-password", null, null, "127.0.0.1");
        assertEquals(7L, result.getUser().getUserId());
        assertTrue(result.getUser().isAuthorCapability());
        verify(identities, never()).selectAuthorCapabilityEnabled(99L);
        assertSame(other, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void missingCapabilityRemainsDenied()
    {
        when(identities.selectAuthorCapabilityEnabled(7L)).thenReturn(null);
        assertFalse(service.ruoyiPasswordLogin("creator-test", "test-only-password", null, null, "127.0.0.1")
                .getUser().isAuthorCapability());
    }

    private AppIdentityContext principal(Long userId)
    {
        return new AppIdentityContext(userId, "01", "0", null, null, null,
                "NOT_SUBMITTED", null, null, null);
    }
}
