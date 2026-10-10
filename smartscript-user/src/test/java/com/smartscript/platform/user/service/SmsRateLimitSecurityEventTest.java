package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppSmsCode;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppSmsCodeMapper;

/**
 * AUTH-06 regression: a frequency-limit rejection must both return 42902 and leave
 * a security-audit record. Without the recorder wiring the limit still returned
 * 42902, so the error code alone could not catch the missing event.
 */
class SmsRateLimitSecurityEventTest
{
    private static class LimitMapper implements AppSmsCodeMapper
    {
        @Override
        public int insertSmsCode(AppSmsCode record)
        {
            return 1;
        }

        @Override
        public AppSmsCode selectLatest(String phone, String scene)
        {
            return null;
        }

        @Override
        public int consumeCode(Long id, java.util.Date now)
        {
            return 0;
        }

        @Override
        public int incrementFailed(Long id)
        {
            return 0;
        }

        @Override
        public int countPhoneToday(String phone, String scene, java.util.Date start)
        {
            return 99;
        }

        @Override
        public int countIpLastHour(String ip, java.util.Date start)
        {
            return 0;
        }
    }

    /** Captures audit records; stands in for the RuoYi logininfor sink. */
    private static class CapturingRecorder extends AppSecurityEventRecorder
    {
        final List<String> events = new ArrayList<>();

        CapturingRecorder()
        {
            super(null);
        }

        @Override
        public void record(String actor, String ip, String event, String detail)
        {
            events.add(event);
        }
    }

    /** Real key prefix, no Redis I/O: cooldown always absent. */
    private static class KeyOnlyRevocation extends AppSessionRevocationService
    {
        KeyOnlyRevocation()
        {
            super(new AppAuthProperties(), null, null);
        }
    }

    /** No cached cooldown entry, so the limit checks are reached. */
    private static class EmptyRedisCache extends com.ruoyi.common.core.redis.RedisCache
    {
        @Override
        public <T> T getCacheObject(String key)
        {
            return null;
        }
    }

    @Test
    void phoneDailyLimitReturns42902AndRecordsSecurityEvent()
    {
        CapturingRecorder recorder = new CapturingRecorder();
        SmsCodeService service = new SmsCodeService(
                new AppAuthProperties(), new LimitMapper(), null, new EmptyRedisCache(),
                new KeyOnlyRevocation(), recorder);

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service.sendCode("13900000002", "LOGIN", "127.0.0.1", "req-1"));
        assertEquals(AppAuthErrorCodes.SMS_RATE_LIMIT, ex.getCode());
        assertEquals(429, ex.getHttpStatus());
        assertTrue(recorder.events.contains("SMS_PHONE_DAILY_LIMIT"),
                "rate-limit rejection must record a security event, got " + recorder.events);
    }
}
