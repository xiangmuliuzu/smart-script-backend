package com.smartscript.platform.user.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.security.context.AuthenticationContextHolder;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.framework.web.service.TokenService;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserMapper;

class AppRuoYiSessionTest
{
    private final TokenService tokens = mock(TokenService.class);
    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final SysLoginService login = mock(SysLoginService.class);
    private final AppUserMapper users = mock(AppUserMapper.class);
    private final AppRuoYiSessionService service = new AppRuoYiSessionService(tokens, manager, login, users);

    @AfterEach
    void cleanup() { AuthenticationContextHolder.clearContext(); }

    private AppUserRecord account()
    {
        AppUserRecord user = new AppUserRecord();
        user.setUserId(8L); user.setUserName("canonical-user"); user.setPhonenumber("13900000007");
        user.setUserType("01"); user.setStatus("0"); user.setDelFlag("0");
        return user;
    }

    private LoginUser session(String domain)
    {
        SysUser user = new SysUser(); user.setUserName("canonical-user"); user.setUserType("01");
        LoginUser session = new LoginUser(8L, null, user, java.util.Set.of());
        session.setCredentialDomain(domain); session.setToken("redis-session-id");
        return session;
    }

    @Test
    void passwordUsesRuoYiManagerCaptchaAndClearsThreadLocal()
    {
        when(manager.authenticate(any())).thenAnswer(i -> {
            var credentials = i.getArgument(0, UsernamePasswordAuthenticationToken.class);
            assertEquals("canonical-user", credentials.getName());
            assertSame(credentials, AuthenticationContextHolder.getContext());
            return new UsernamePasswordAuthenticationToken(session("app"), null, java.util.List.of());
        });
        service.authenticate(account(), "Passw0rdX", "1234", "captcha-id");
        verify(login).validateCaptcha("canonical-user", "1234", "captcha-id");
        verify(manager).authenticate(any());
        assertNull(AuthenticationContextHolder.getContext());
    }

    @Test
    void legacyRuoYiPasswordUsesTheSameAuthenticationManager()
    {
        when(manager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(session("app"), null, java.util.List.of()));
        service.authenticate(account(), "123456", null, null);
        verify(manager).authenticate(any());
        assertNull(AuthenticationContextHolder.getContext());
    }

    @Test
    void badPasswordUsesSafeMessageAndAlwaysClearsContext()
    {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));
        AppAuthException error = assertThrows(AppAuthException.class,
                () -> service.authenticate(account(), "WrongPass1", null, null));
        assertEquals(400, error.getHttpStatus());
        assertEquals("用户不存在/密码错误", error.getMessage());
        assertNull(AuthenticationContextHolder.getContext());
    }

    @Test
    void issueUsesRealTokenServiceWithEmptyAdminPermissions()
    {
        when(tokens.createToken(any())).thenReturn("signed-token");
        assertEquals("signed-token", service.issue(account()));
        ArgumentCaptor<LoginUser> captor = ArgumentCaptor.forClass(LoginUser.class);
        verify(tokens).createToken(captor.capture());
        assertEquals("app", captor.getValue().getCredentialDomain());
        assertTrue(captor.getValue().getPermissions().isEmpty());
        assertEquals(8L, captor.getValue().getUserId());
    }

    @Test
    void privateRequestRechecksAccountAndRenewsRuoYiSession()
    {
        LoginUser session = session("app");
        when(tokens.getLoginUser("signed-token")).thenReturn(session);
        when(users.selectById(8L)).thenReturn(account());
        when(users.selectRealNameStatus(8L)).thenReturn("PENDING");
        AppIdentityContext identity = service.parseAndValidate("signed-token");
        assertEquals(8L, identity.getUserId());
        assertEquals("redis-session-id", identity.getJti());
        assertEquals("PENDING", identity.getRealNameStatus());
        verify(tokens).verifyToken(session);
    }

    @Test
    void adminTokenOrDisabledOrAdminAccountIsRejectedWithoutRenewal()
    {
        when(tokens.getLoginUser("admin-token")).thenReturn(session("admin"));
        assertEquals(403, assertThrows(AppAuthException.class,
                () -> service.parseAndValidate("admin-token")).getHttpStatus());
        AppUserRecord account = account(); account.setStatus("1");
        when(tokens.getLoginUser("app-token")).thenReturn(session("app"));
        when(users.selectById(8L)).thenReturn(account);
        assertEquals(403, assertThrows(AppAuthException.class,
                () -> service.parseAndValidate("app-token")).getHttpStatus());
        account.setStatus("0"); account.setUserType("00");
        assertThrows(AppAuthException.class, () -> service.issue(account));
        verify(tokens, never()).verifyToken(any());
        verify(tokens, never()).createToken(any());
    }

    @Test
    void expiredOrLegacyTokenCannotRestoreIdentity()
    {
        assertEquals(401, assertThrows(AppAuthException.class,
                () -> service.parseAndValidate("legacy-token")).getHttpStatus());
        verifyNoInteractions(users);
    }
}
