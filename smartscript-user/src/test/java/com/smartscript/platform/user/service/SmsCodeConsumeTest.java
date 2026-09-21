package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
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

    private static SmsCodeService service(StubMapper mapper)
    {
        return new SmsCodeService(new AppAuthProperties(), mapper, null, null, null);
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
    void freshCodeIsConsumedOnce()
    {
        StubMapper mapper = new StubMapper();
        mapper.newest = row(CODE, null, future());

        assertDoesNotThrow(() -> service(mapper).consumeOnce("13900000001", "LOGIN", CODE));
        assertEquals(1, mapper.consumeCalls);
    }
}
