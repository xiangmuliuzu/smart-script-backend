package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.RegisterRequest;
import com.smartscript.platform.user.dto.SmsLoginRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppIdentityMapper;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.security.AppRuoYiSessionService;

class AppRegistrationNicknameTest
{
    private final AppUserMapper users = mock(AppUserMapper.class);
    private final AppRuoYiSessionService sessions = mock(AppRuoYiSessionService.class);
    private final SmsCodeService sms = mock(SmsCodeService.class);
    private final AgreementService agreements = mock(AgreementService.class);
    private AppAuthenticationService service;

    @BeforeEach
    void setup()
    {
        BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("test-only-password-hash");
        AppIdentityMapper identities = mock(AppIdentityMapper.class);
        when(identities.selectRoleCodes(anyLong())).thenReturn(List.of());
        when(identities.selectPermissionCodes(anyLong())).thenReturn(List.of());
        when(sessions.issue(any())).thenReturn("test-only-token");
        when(users.insertAppUser(any())).thenAnswer(call -> {
            AppUserRecord user = call.getArgument(0);
            user.setUserId(7L);
            return 1;
        });
        service = new AppAuthenticationService(users, sms, agreements, sessions,
                mock(AppSessionRevocationService.class), encoder, identities, null);
    }

    @Test
    void explicitRegistrationImmediatelyReturnsRandomNameInsteadOfPhone()
    {
        var result = service.register(registerRequest(), "127.0.0.1");
        assertGeneratedName(result.getUser().getNickname());
        assertEquals("138****8000", result.getUser().getPhoneMasked());
        verify(sms).consumeOnce("13800138000", "REGISTER", "123456");
        verify(agreements).validateCurrent(List.of());
    }

    @Test
    void emailRegistrationUsesVerifiedNormalizedAddressAndUniqueBinding()
    {
        RegisterRequest request = registerRequest();
        request.setPhone(null);
        request.setEmail("Writer@Example.com");
        var result = service.register(request, "127.0.0.1");
        assertGeneratedName(result.getUser().getNickname());
        verify(sms).consumeEmailCode("writer@example.com", "123456");
        verify(users).insertEmailBinding(7L, "writer@example.com");
        var captor = org.mockito.ArgumentCaptor.forClass(AppUserRecord.class);
        verify(users).insertAppUser(captor.capture());
        assertEquals("writer@example.com", captor.getValue().getEmail());
        assertNull(captor.getValue().getPhonenumber());
    }

    @Test
    void emailRegistrationRejectsExistingOrConflictingIdentifiers()
    {
        RegisterRequest request = registerRequest();
        request.setEmail("writer@example.com");
        assertThrows(AppAuthException.class, () -> service.register(request, "127.0.0.1"));
        request.setPhone(null);
        when(users.selectByEmail("writer@example.com")).thenReturn(existingUser());
        assertThrows(AppAuthException.class, () -> service.register(request, "127.0.0.1"));
        verify(users, never()).insertAppUser(any());
        verifyNoInteractions(sessions);
    }

    @Test
    void concurrentEmailRegistrationDoesNotIssueSession()
    {
        RegisterRequest request = registerRequest();
        request.setPhone(null);
        request.setEmail("writer@example.com");
        doThrow(new DuplicateKeyException("duplicate")).when(users).insertEmailBinding(7L, "writer@example.com");
        assertThrows(AppAuthException.class, () -> service.register(request, "127.0.0.1"));
        verifyNoInteractions(sessions);
    }

    @Test
    void emailCodeLoginCreatesVerifiedAccountAndUsesDedicatedScene()
    {
        SmsLoginRequest request = smsRequest();
        request.setPhone(null);
        request.setEmail("Writer@Example.com");
        var result = service.smsLogin(request, "127.0.0.1");
        assertGeneratedName(result.getUser().getNickname());
        assertNull(result.getUser().getPhoneMasked());
        verify(sms).consumeEmailCode("writer@example.com", "LOGIN", "123456");
        verify(users).insertEmailBinding(7L, "writer@example.com");
    }

    @Test
    void emailPasswordResetOnlyUsesVerifiedBinding()
    {
        var request = new com.smartscript.platform.user.dto.PasswordResetRequest();
        request.setEmail("Writer@Example.com");
        request.setCode("123456");
        request.setNewPassword("Passw0rd123");
        when(users.selectByVerifiedEmail("writer@example.com")).thenReturn(existingUser());
        service.resetPassword(request);
        verify(sms).consumeEmailCode("writer@example.com", "RESET_PASSWORD", "123456");
        verify(users).updatePassword(anyLong(), eq("test-only-password-hash"));
        verify(users, never()).selectByEmail(anyString());
    }

    @Test
    void firstSmsLoginUsesTheSameRandomNamingRuleForEveryNewAccount()
    {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 100; i++)
        {
            String nickname = service.smsLogin(smsRequest(), "127.0.0.1").getUser().getNickname();
            assertGeneratedName(nickname);
            assertTrue(generated.add(nickname), "new accounts must not share a display name");
        }
    }

    @Test
    void databaseNameConflictsRegenerateBothNamesUntilInsertSucceeds()
    {
        List<String> attemptedNames = new ArrayList<>();
        List<String> attemptedUserNames = new ArrayList<>();
        doAnswer(call -> {
            AppUserRecord user = call.getArgument(0);
            attemptedNames.add(user.getNickName());
            attemptedUserNames.add(user.getUserName());
            if (attemptedNames.size() < 4)
            {
                throw new DuplicateKeyException("uk_sys_user_app_display_name");
            }
            user.setUserId(7L);
            return 1;
        }).when(users).insertAppUser(any());

        var result = service.register(registerRequest(), "127.0.0.1");
        assertEquals(4, attemptedNames.size());
        attemptedNames.forEach(AppRegistrationNicknameTest::assertGeneratedName);
        assertEquals(4, new HashSet<>(attemptedNames).size());
        assertEquals(4, new HashSet<>(attemptedUserNames).size());
        assertEquals(attemptedNames.get(3), result.getUser().getNickname());
        verify(sessions, times(1)).issue(any());
    }

    @Test
    void repeatedConflictsFailWithoutReturningDuplicateNameOrIssuingSession()
    {
        doThrow(new DuplicateKeyException("internal database detail"))
                .when(users).insertAppUser(any());
        AppAuthException error = assertThrows(AppAuthException.class,
                () -> service.register(registerRequest(), "127.0.0.1"));
        assertEquals(AppAuthErrorCodes.SYSTEM_ERROR, error.getCode());
        assertEquals(503, error.getHttpStatus());
        assertFalse(error.getMessage().contains("internal database detail"));
        verify(users, times(10)).insertAppUser(any());
        verifyNoInteractions(sessions);
        verify(agreements, never()).recordAcceptances(anyLong(), anyList(), anyString(), anyString());
    }

    @Test
    void existingAccountKeepsItsChosenNameWhenLoggingInAgain()
    {
        AppUserRecord existing = existingUser();
        when(users.selectByPhone("13800138000")).thenReturn(existing);
        assertEquals("原有名称", service.smsLogin(smsRequest(), "127.0.0.1").getUser().getNickname());
        verify(users, never()).insertAppUser(any());
    }

    @Test
    void concurrentPhoneRegistrationReturnsExistingAccountWithoutRenamingIt()
    {
        AppUserRecord existing = existingUser();
        when(users.selectByPhone("13800138000")).thenReturn(null, existing);
        doThrow(new DuplicateKeyException("uk_sys_user_phonenumber")).when(users).insertAppUser(any());
        assertEquals("原有名称", service.smsLogin(smsRequest(), "127.0.0.1").getUser().getNickname());
        verify(users, times(1)).insertAppUser(any());
        assertEquals("原有名称", existing.getNickName());
    }

    private static void assertGeneratedName(String name)
    {
        assertNotNull(name);
        assertTrue(name.matches("用户[a-z0-9]{13}"), name);
        assertTrue(name.substring(2).matches(".*[a-z].*"), "suffix must contain a letter: " + name);
        assertTrue(name.substring(2).matches(".*[0-9].*"), "suffix must contain a digit: " + name);
    }

    private static AppUserRecord existingUser()
    {
        AppUserRecord user = new AppUserRecord();
        user.setUserId(7L);
        user.setNickName("原有名称");
        user.setPhonenumber("13800138000");
        user.setUserType("01");
        user.setStatus("0");
        user.setDelFlag("0");
        return user;
    }

    private static RegisterRequest registerRequest()
    {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setCode("123456");
        request.setPassword("TestPass123");
        request.setDeviceId("test-device");
        request.setAgreementAcceptances(List.of());
        return request;
    }

    private static SmsLoginRequest smsRequest()
    {
        SmsLoginRequest request = new SmsLoginRequest();
        request.setPhone("13800138000");
        request.setCode("123456");
        request.setDeviceId("test-device");
        request.setAgreementAcceptances(List.of());
        return request;
    }
}
