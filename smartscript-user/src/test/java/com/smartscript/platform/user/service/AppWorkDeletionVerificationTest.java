package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import com.ruoyi.common.exception.ServiceException;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.domain.AppSmsCode;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.mapper.AppSmsCodeMapper;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.util.AppHashes;

class AppWorkDeletionVerificationTest
{
    private static final String PHONE = "13900000001";
    private final AppUserMapper users = mock(AppUserMapper.class);
    private final AppSmsCodeMapper codes = mock(AppSmsCodeMapper.class);
    private final AppAuthProperties properties = new AppAuthProperties();
    private final SmsCodeService sms = new SmsCodeService(properties, codes, null, null, null, null,
            mock(PlatformTransactionManager.class));
    private final AppWorkDeletionVerification verification =
            new AppWorkDeletionVerification(users, sms, properties);

    @BeforeEach
    void setUp()
    {
        AppUserRecord user = new AppUserRecord();
        user.setUserId(12L);
        user.setStatus("0");
        user.setPhonenumber(PHONE);
        when(users.selectById(12L)).thenReturn(user);
    }

    @Test
    void publicSmsEndpointCannotSendDeletionCodesForAnArbitraryPhone()
    {
        SmsCodeService sender = mock(SmsCodeService.class);
        var controller = new com.smartscript.platform.user.controller.AppAuthController(null, sender, properties,
                mock(com.smartscript.platform.review.service.RiskCheckService.class));
        var request = new com.smartscript.platform.user.dto.SmsSendRequest();
        request.setPhone(PHONE);
        request.setScene("DELETE_WORK");
        var error = assertThrows(com.smartscript.platform.user.exception.AppAuthException.class,
                () -> controller.sendSms(request, null, null));
        assertEquals(40000, error.getCode());
        verifyNoInteractions(sender);
    }

    @Test
    void loginCodesCannotAuthorizeDeletion()
    {
        when(codes.selectLatest(PHONE, "LOGIN")).thenReturn(freshCode());
        assertEquals(40002, assertThrows(ServiceException.class,
                () -> verification.consumeCode(12L, "123456")).getCode());
        verify(codes, never()).consumeCode(any(), any());
    }

    @Test
    void deletionCodeIsConsumedOnlyOnce()
    {
        AppSmsCode code = freshCode();
        when(codes.selectLatest(PHONE, "DELETE_WORK")).thenReturn(code);
        when(codes.consumeCode(eq(1L), any())).thenAnswer(invocation -> {
            code.setUsedAt(invocation.getArgument(1));
            return 1;
        });
        verification.consumeCode(12L, "123456");
        assertEquals(40901, assertThrows(ServiceException.class,
                () -> verification.consumeCode(12L, "123456")).getCode());
        verify(codes, times(1)).consumeCode(eq(1L), any());
    }

    @Test
    void expiredDeletionCodeIsRejected()
    {
        AppSmsCode code = freshCode();
        code.setExpiresAt(new Date(0));
        when(codes.selectLatest(PHONE, "DELETE_WORK")).thenReturn(code);
        assertEquals(40002, assertThrows(ServiceException.class,
                () -> verification.consumeCode(12L, "123456")).getCode());
        verify(codes, never()).consumeCode(any(), any());
    }

    @Test
    void missingPhoneOrDisabledAccountCannotAuthorizeDeletion()
    {
        when(users.selectById(12L)).thenReturn(null);
        assertEquals(40301, assertThrows(ServiceException.class,
                () -> verification.consumeCode(12L, "123456")).getCode());
        AppUserRecord user = new AppUserRecord();
        user.setStatus("0");
        when(users.selectById(12L)).thenReturn(user);
        assertEquals(40000, assertThrows(ServiceException.class,
                () -> verification.consumeCode(12L, "123456")).getCode());
        verifyNoInteractions(codes);
    }

    @Test
    void sendingUsesStoredPhoneAndDedicatedSceneAndReturnsOnlyMaskedPhone()
    {
        SmsCodeService sender = mock(SmsCodeService.class);
        var result = new AppWorkDeletionVerification(users, sender, properties)
                .sendCode(12L, "127.0.0.1", "request");
        verify(sender).sendCode(PHONE, "DELETE_WORK", "127.0.0.1", "request");
        assertEquals(true, result.get("sent"));
        assertNotEquals(PHONE, result.get("phoneMasked"));
        assertFalse(result.containsKey("code"));
    }

    @Test
    void emailOnlyUserCanVerifyDeletionWithoutExposingAddress()
    {
        AppUserRecord user = new AppUserRecord();
        user.setUserId(12L);
        user.setStatus("0");
        when(users.selectById(12L)).thenReturn(user);
        when(users.selectVerifiedEmailByUserId(12L)).thenReturn("author@example.com");
        SmsCodeService sender = mock(SmsCodeService.class);
        var verifier = new AppWorkDeletionVerification(users, sender, properties);
        var result = verifier.sendCode(12L, "127.0.0.1", "request");
        assertEquals("***@example.com", result.get("phoneMasked"));
        verify(sender).sendEmailCode("author@example.com", "DELETE_WORK", "127.0.0.1", "request");
        verifier.consumeCode(12L, "123456");
        verify(sender).consumeEmailCode("author@example.com", "DELETE_WORK", "123456");
    }

    @Test
    void publicEmailEndpointCannotIssueDeletionCode()
    {
        SmsCodeService sender = mock(SmsCodeService.class);
        var controller = new com.smartscript.platform.user.controller.AppAuthController(null, sender, properties,
                mock(com.smartscript.platform.review.service.RiskCheckService.class));
        var request = new com.smartscript.platform.user.dto.EmailCodeRequest();
        request.setEmail("author@example.com");
        request.setScene("DELETE_WORK");
        var http = mock(jakarta.servlet.http.HttpServletRequest.class);
        assertThrows(com.smartscript.platform.user.exception.AppAuthException.class, () -> controller.sendEmail(request, http, null));
        verifyNoInteractions(sender);
    }

    private static AppSmsCode freshCode()
    {
        AppSmsCode code = new AppSmsCode();
        code.setId(1L);
        code.setPhone(PHONE);
        code.setScene("DELETE_WORK");
        code.setCodeHash(AppHashes.sha256Hex("123456"));
        code.setExpiresAt(new Date(System.currentTimeMillis() + 300000));
        code.setFailedAttempts(0);
        return code;
    }
}
