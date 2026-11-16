package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppSmsCode;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppSmsCodeMapper;
import com.smartscript.platform.user.util.AppHashes;

/**
 * Regression guard for AUTH-04: a consumed or expired SMS code must return its own
 * stable contract code and must never fall back to an older still-unused row.
 */
class SmsCodeConsumeTest
{
    private static final String CODE = "123456";

    /** Returns the newest row only; records how many rows were consumed. */
    private static class StubMapper implements AppSmsCodeMapper
    {
        AppSmsCode newest;
        int consumeCalls;

        @Override
        public int insertSmsCode(AppSmsCode record)
        {
            newest = record;
            return 1;
        }

        @Override
        public AppSmsCode selectLatest(String phone, String scene)
        {
            return newest;
        }

        @Override
        public int consumeCode(Long id, java.util.Date now)
        {
            consumeCalls++;
            return 1;
        }

        @Override
        public int incrementFailed(Long id)
        {
            if (newest != null && newest.getFailedAttempts() != null)
            {
                newest.setFailedAttempts(newest.getFailedAttempts() + 1);
            }
            return 1;
        }

        @Override
        public int countPhoneToday(String phone, String scene, java.util.Date start)
        {
            return 0;
        }

        @Override
        public int countIpLastHour(String ip, java.util.Date start)
        {
            return 0;
        }
    }

    /** No-op manager: the stub already applies increments, so no real tx is needed. */
    private static class NoopTransactionManager implements PlatformTransactionManager
    {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException
        {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException
        {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException
        {
        }
    }

    private static SmsCodeService service(StubMapper mapper)
    {
        return new SmsCodeService(new AppAuthProperties(), mapper, null, null, null, null,
                new NoopTransactionManager());
    }

    private static AppSmsCode row(String code, java.util.Date usedAt, java.util.Date expiresAt)
    {
        AppSmsCode r = new AppSmsCode();
        r.setId(1L);
        r.setPhone("13900000001");
        r.setScene("LOGIN");
        r.setCodeHash(AppHashes.sha256Hex(code));
        r.setFailedAttempts(0);
        r.setUsedAt(usedAt);
        r.setExpiresAt(expiresAt);
        return r;
    }

    private static java.util.Date future()
    {
        return new java.util.Date(System.currentTimeMillis() + 300_000L);
    }

    @Test
    void emailCodeUsesNormalizedAddressAndExistingOneTimeChecks()
    {
        StubMapper mapper = new StubMapper();
        mapper.newest = row(CODE, null, future());
        SmsCodeService service = service(mapper);
        assertEquals("writer@example.com", SmsCodeService.normalizeEmail(" Writer@Example.com "));
        assertThrows(AppAuthException.class, () -> SmsCodeService.normalizeEmail("bad@address"));
        assertDoesNotThrow(() -> service.consumeEmailCode("Writer@Example.com", CODE));
        assertEquals(1, mapper.consumeCalls);
        mapper.newest.setUsedAt(new java.util.Date());
        assertThrows(AppAuthException.class, () -> service.consumeEmailCode("writer@example.com", CODE));
    }

    @Test
    void emailSendStoresOnlyHashedCodeAndUsesEmailProvider()
    {
        var mapper = org.mockito.Mockito.mock(AppSmsCodeMapper.class);
        var redis = org.mockito.Mockito.mock(com.ruoyi.common.core.redis.RedisCache.class);
        var revocation = org.mockito.Mockito.mock(AppSessionRevocationService.class);
        org.mockito.Mockito.when(revocation.key(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> call.getArgument(0));
        var sender = org.mockito.Mockito.mock(RegistrationEmailSender.class);
        var service = new SmsCodeService(new AppAuthProperties(), mapper,
                org.mockito.Mockito.mock(com.smartscript.platform.user.integration.SmsProvider.class),
                redis, revocation, null, new NoopTransactionManager());
        service.setEmailSender(sender);
        service.sendEmailCode("Writer@Example.com", "127.0.0.1", null);
        var row = org.mockito.ArgumentCaptor.forClass(AppSmsCode.class);
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mapper).insertSmsCode(row.capture());
        org.mockito.Mockito.verify(sender).send(org.mockito.Mockito.eq("writer@example.com"), code.capture(), org.mockito.Mockito.anyInt());
        assertEquals("email:writer@example.com", row.getValue().getPhone());
        assertEquals("EMAIL_REGISTER", row.getValue().getScene());
        assertEquals(AppHashes.sha256Hex(code.getValue()), row.getValue().getCodeHash());
        assertTrue(code.getValue().matches("\\d{6}"));
    }

    @Test
    void alreadyUsedCodeIsRejectedWith40901()
    {
        StubMapper mapper = new StubMapper();
        mapper.newest = row(CODE, new java.util.Date(), future());
        SmsCodeService service = service(mapper);

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service.consumeOnce("13900000001", "LOGIN", CODE));
        assertEquals(AppAuthErrorCodes.SMS_CODE_USED, ex.getCode());
        assertEquals(409, ex.getHttpStatus());
        assertEquals(0, mapper.consumeCalls, "used code must not be consumed again");
    }

    @Test
    void expiredCodeIsRejectedWith40002()
    {
        StubMapper mapper = new StubMapper();
        mapper.newest = row(CODE, null, new java.util.Date(System.currentTimeMillis() - 1000L));

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service(mapper).consumeOnce("13900000001", "LOGIN", CODE));
        assertEquals(AppAuthErrorCodes.SMS_CODE_EXPIRED, ex.getCode());
    }

    @Test
    void missingExpiryIsRejectedWith40002()
    {
        StubMapper mapper = new StubMapper();
        mapper.newest = row(CODE, null, null);

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service(mapper).consumeOnce("13900000001", "LOGIN", CODE));
        assertEquals(AppAuthErrorCodes.SMS_CODE_EXPIRED, ex.getCode());
        assertEquals(0, mapper.consumeCalls);
    }

    @Test
    void wrongCodeIsRejectedWith40001()
    {
        StubMapper mapper = new StubMapper();
        mapper.newest = row(CODE, null, future());

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service(mapper).consumeOnce("13900000001", "LOGIN", "000000"));
        assertEquals(AppAuthErrorCodes.SMS_CODE_INVALID, ex.getCode());
        assertEquals(1, mapper.newest.getFailedAttempts());
    }

    @Test
    void shortButWellFormedWrongCodeIsRejectedWith40001()
    {
        StubMapper mapper = new StubMapper();
        mapper.newest = row(CODE, null, future());

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service(mapper).consumeOnce("13900000001", "LOGIN", "1234"));
        assertEquals(AppAuthErrorCodes.SMS_CODE_INVALID, ex.getCode());
    }

    @Test
    void freshCodeIsConsumedOnce()
    {
        StubMapper mapper = new StubMapper();
        mapper.newest = row(CODE, null, future());

        assertDoesNotThrow(() -> service(mapper).consumeOnce("13900000001", "LOGIN", CODE));
        assertEquals(1, mapper.consumeCalls);
    }

    @Test
    void lockedAfterMaxFailedAttemptsEvenWithCorrectCode()
    {
        StubMapper mapper = new StubMapper();
        mapper.newest = row(CODE, null, future());
        SmsCodeService service = service(mapper);

        // max-failed-attempts defaults to 5: five wrong tries must arm the lock,
        // so the brute-force window on one code is bounded.
        for (int i = 0; i < 5; i++)
        {
            assertThrows(AppAuthException.class,
                    () -> service.consumeOnce("13900000001", "LOGIN", "000000"));
        }
        assertEquals(5, mapper.newest.getFailedAttempts());

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service.consumeOnce("13900000001", "LOGIN", CODE));
        assertEquals(AppAuthErrorCodes.SMS_CODE_INVALID, ex.getCode());
        assertEquals(0, mapper.consumeCalls, "locked code must not be consumed");
    }
}
