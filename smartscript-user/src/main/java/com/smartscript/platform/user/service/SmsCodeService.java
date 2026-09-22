package com.smartscript.platform.user.service;

import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.redis.RedisCache;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppSmsCode;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.integration.MockSmsProvider;
import com.smartscript.platform.user.integration.SmsProvider;
import com.smartscript.platform.user.mapper.AppSmsCodeMapper;
import com.smartscript.platform.user.util.AppHashes;

@Service
public class SmsCodeService
{
    private static final Set<String> ALLOWED_SCENES = Set.of(
            "LOGIN", "REGISTER", "SET_PASSWORD", "RESET_PASSWORD",
            "CHANGE_PHONE_OLD", "CHANGE_PHONE_NEW");

    private final AppAuthProperties properties;
    private final AppSmsCodeMapper mapper;
    private final SmsProvider smsProvider;
    private final RedisCache redisCache;
    private final AppSessionRevocationService revocationService;
    private final AppSecurityEventRecorder securityEvents;

    public SmsCodeService(AppAuthProperties properties,
            AppSmsCodeMapper mapper,
            SmsProvider smsProvider,
            RedisCache redisCache,
            AppSessionRevocationService revocationService,
            AppSecurityEventRecorder securityEvents)
    {
        this.properties = properties;
        this.mapper = mapper;
        this.smsProvider = smsProvider;
        this.redisCache = redisCache;
        this.revocationService = revocationService;
        this.securityEvents = securityEvents;
    }

    /** AUTH-06: frequency-limit decisions must leave a security-audit record. */
    private void recordSecurityEvent(String phone, String ip, String event, int observed)
    {
        if (securityEvents != null)
        {
            securityEvents.record(AppHashes.maskPhone(phone), ip, event,
                    "scene limit observed=" + observed);
        }
    }

    public void sendCode(String phone, String scene, String ip, String requestId)
    {
        if (phone == null || !phone.matches("^1\\d{10}$"))
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "invalid phone");
        }
        if (scene == null || !ALLOWED_SCENES.contains(scene))
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "invalid scene");
        }

        String cooldownKey = revocationService.key("sms:cooldown:" + phone + ":" + scene);
        Object cooling = redisCache.getCacheObject(cooldownKey);
        if (cooling != null)
        {
            long remain = redisCache.getExpire(cooldownKey);
            throw new AppAuthException(AppAuthErrorCodes.SMS_COOLDOWN, 429, "sms cooldown",
                    java.util.Map.of("retryAfterSeconds", Math.max(remain, 1L)));
        }

        Date dayStart = startOfDay();
        Date hourStart = new Date(System.currentTimeMillis() - 3600_000L);
        int phoneToday = mapper.countPhoneToday(phone, scene, dayStart);
        if (phoneToday >= properties.getSms().getPhoneDailyLimit())
        {
            recordSecurityEvent(phone, ip, "SMS_PHONE_DAILY_LIMIT", phoneToday);
            throw new AppAuthException(AppAuthErrorCodes.SMS_RATE_LIMIT, 429, "phone rate limit");
        }
        if (ip != null && !ip.isBlank())
        {
            int ipHour = mapper.countIpLastHour(ip, hourStart);
            if (ipHour >= properties.getSms().getIpHourlyLimit())
            {
                recordSecurityEvent(phone, ip, "SMS_IP_HOURLY_LIMIT", ipHour);
                throw new AppAuthException(AppAuthErrorCodes.SMS_RATE_LIMIT, 429, "ip rate limit");
            }
        }

        // requestId idempotency: same request does not double-send
        if (requestId != null && !requestId.isBlank())
        {
            String reqKey = revocationService.key("sms:send:req:" + requestId);
            Boolean first = redisCache.redisTemplate.opsForValue().setIfAbsent(reqKey, "1", 60, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(first))
            {
                return;
            }
        }

        String code = AppHashes.randomNumericCode(properties.getSms().getCodeLength());
        if (smsProvider instanceof MockSmsProvider mock)
        {
            code = mock.resolveCode(phone, scene, code);
        }
        String hash = AppHashes.sha256Hex(code);
        Date expiresAt = new Date(System.currentTimeMillis() + properties.getSms().getCodeTtlSeconds() * 1000L);
        AppSmsCode record = new AppSmsCode();
        record.setPhone(phone);
        record.setScene(scene);
        record.setCodeHash(hash);
        record.setRequestIp(ip);
        record.setFailedAttempts(0);
        record.setExpiresAt(expiresAt);
        mapper.insertSmsCode(record);
        smsProvider.send(phone, scene, code);
        redisCache.setCacheObject(cooldownKey, "1", properties.getSms().getCooldownSeconds(), TimeUnit.SECONDS);
    }

    public void consumeOnce(String phone, String scene, String code)
    {
        if (phone == null || code == null || code.isBlank())
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "invalid sms payload");
        }
        if (scene == null || !ALLOWED_SCENES.contains(scene))
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "invalid scene");
        }
        Date now = new Date();
        // Always evaluate the newest row for this phone+scene. Falling back to an older
        // still-unused row would let an already-consumed code succeed again.
        AppSmsCode latest = mapper.selectLatest(phone, scene);
        if (latest == null)
        {
            throw new AppAuthException(AppAuthErrorCodes.SMS_CODE_EXPIRED, 400, "sms code expired");
        }
        if (latest.getUsedAt() != null)
        {
            throw new AppAuthException(AppAuthErrorCodes.SMS_CODE_USED, 409, "sms code already used");
        }
        if (latest.getExpiresAt() != null && latest.getExpiresAt().before(now))
        {
            throw new AppAuthException(AppAuthErrorCodes.SMS_CODE_EXPIRED, 400, "sms code expired");
        }
        if (latest.getFailedAttempts() != null
                && latest.getFailedAttempts() >= properties.getSms().getMaxFailedAttempts())
        {
            throw new AppAuthException(AppAuthErrorCodes.SMS_CODE_INVALID, 400, "sms code invalid");
        }
        String hash = AppHashes.sha256Hex(code);
        if (!hash.equals(latest.getCodeHash()))
        {
            mapper.incrementFailed(latest.getId());
            throw new AppAuthException(AppAuthErrorCodes.SMS_CODE_INVALID, 400, "sms code invalid");
        }
        int updated = mapper.consumeCode(latest.getId(), now);
        if (updated == 0)
        {
            // concurrent consume or already used
            throw new AppAuthException(AppAuthErrorCodes.SMS_CODE_USED, 409, "sms code already used");
        }
    }

    private static Date startOfDay()
    {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
