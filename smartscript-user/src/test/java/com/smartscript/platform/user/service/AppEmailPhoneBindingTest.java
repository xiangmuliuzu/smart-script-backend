package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.PhoneChangeVerifyRequest;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.security.AppIdentityContext;

class AppEmailPhoneBindingTest {
    @Test
    void emailOnlyAccountVerifiesItsOwnAddressBeforeBindingPhone() {
        var users = mock(AppUserMapper.class);
        var sms = mock(SmsCodeService.class);
        var tokens = mock(PhoneChangeTokenStore.class);
        var user = new AppUserRecord();
        user.setUserId(12L);
        user.setStatus("0");
        when(users.selectById(12L)).thenReturn(user);
        when(users.selectVerifiedEmailByUserId(12L)).thenReturn("author@example.com");
        when(tokens.issue(12L, "device")).thenReturn("step-up-token");
        var service = new AppPhoneChangeService(users, mock(AppUserCenterMapper.class), sms,
                tokens, mock(AppSessionRevocationService.class));
        var identity = new AppIdentityContext(12L, "01", "0", null, "name", null, null, 1L, "jti", null);
        var result = service.sendOldPhoneCode(identity, "127.0.0.1", "request");
        assertEquals("***@example.com", result.get("phoneMasked"));
        verify(sms).sendEmailCode("author@example.com", "CHANGE_PHONE_OLD", "127.0.0.1", "request");
        var request = new PhoneChangeVerifyRequest();
        request.setCode("123456");
        request.setDeviceId("device");
        service.verifyOldPhone(identity, request, "127.0.0.1", "request");
        verify(sms).consumeEmailCode("author@example.com", "CHANGE_PHONE_OLD", "123456");
        verify(tokens).issue(12L, "device");
    }

    @Test
    void bindAndChangeRequireCurrentAccountProofAndNewEmailCode() {
        var users = mock(AppUserMapper.class);
        var codes = mock(SmsCodeService.class);
        var tokens = mock(PhoneChangeTokenStore.class);
        var revocation = mock(AppSessionRevocationService.class);
        var user = new AppUserRecord(); user.setUserId(12L); user.setStatus("0");
        when(users.selectById(12L)).thenReturn(user);
        when(tokens.consume("step-up", 12L, "device")).thenReturn(true);
        var service = new AppPhoneChangeService(users, mock(AppUserCenterMapper.class), codes, tokens, revocation);
        var identity = new AppIdentityContext(12L, "01", "0", null, "name", null, null, 1L, "jti", null);
        var request = new com.smartscript.platform.user.dto.EmailChangeRequest();
        request.setNewEmail("Author@Example.com"); request.setCode("123456");
        request.setStepUpToken("step-up"); request.setDeviceId("device");
        service.sendNewEmailCode(identity, request, "127.0.0.1", "request");
        verify(codes).sendEmailCode("author@example.com", "CHANGE_EMAIL_NEW", "127.0.0.1", "request");
        service.confirmEmailChange(identity, request);
        verify(codes).consumeEmailCode("author@example.com", "CHANGE_EMAIL_NEW", "123456");
        verify(users).insertEmailBinding(12L, "author@example.com");
        verify(users).updateEmail(12L, "author@example.com");
        verify(revocation).revokeAllForUser(12L, "EMAIL_CHANGE");
        when(users.selectVerifiedEmailByUserId(12L)).thenReturn("old@example.com");
        service.confirmEmailChange(identity, request);
        verify(users).updateEmailBinding(12L, "author@example.com");
    }

    @Test
    void bindRejectsMissingProofAndOccupiedEmail() {
        var users = mock(AppUserMapper.class);
        var codes = mock(SmsCodeService.class);
        var tokens = mock(PhoneChangeTokenStore.class);
        var user = new AppUserRecord(); user.setUserId(12L); user.setStatus("0");
        when(users.selectById(12L)).thenReturn(user);
        var service = new AppPhoneChangeService(users, mock(AppUserCenterMapper.class), codes, tokens, mock(AppSessionRevocationService.class));
        var identity = new AppIdentityContext(12L, "01", "0", null, "name", null, null, 1L, "jti", null);
        var request = new com.smartscript.platform.user.dto.EmailChangeRequest();
        request.setNewEmail("author@example.com"); request.setCode("123456");
        request.setStepUpToken("bad"); request.setDeviceId("device");
        assertThrows(com.smartscript.platform.user.exception.AppAuthException.class, () -> service.confirmEmailChange(identity, request));
        verify(codes, never()).consumeEmailCode(anyString(), anyString(), anyString());
        verify(users, never()).updateEmail(anyLong(), anyString());
        var other = new AppUserRecord(); other.setUserId(99L);
        when(users.selectByEmail("author@example.com")).thenReturn(other);
        assertThrows(com.smartscript.platform.user.exception.AppAuthException.class, () -> service.sendNewEmailCode(identity, request, "127.0.0.1", "request"));
        verify(codes, never()).sendEmailCode(anyString(), anyString(), anyString(), anyString());
    }
}
