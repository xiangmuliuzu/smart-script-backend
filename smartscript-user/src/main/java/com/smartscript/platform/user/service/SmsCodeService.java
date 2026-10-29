package com.smartscript.platform.user.service;

import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
            "CHANGE_PHONE_OLD", "CHANGE_PHONE_NEW", "DELETE_WORK", "EMAIL_REGISTER", "EMAIL_LOGIN", "EMAIL_RESET_PASSWORD", "EMAIL_DELETE_WORK", "EMAIL_CHANGE_PHONE_OLD", "EMAIL_CHANGE_EMAIL_NEW");

    private RegistrationEmailSender emailSender;

    @org.springframework.beans.factory.annotation.Autowired
    public void setEmailSender(RegistrationEmailSender emailSender) { this.emailSender = emailSender; }

    public static String normalizeEmail(String email)
    {
        String value = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.length() > 50 || !value.matches("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$"))
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "请输入有效邮箱地址");
        return value;
    }

    public void sendEmailCode(String email, String ip, String requestId)
    {
        sendEmailCode(email, "REGISTER", ip, requestId);
    }

    public void sendEmailCode(String email, String scene, String ip, String requestId)
    {
        if (!Set.of("REGISTER", "LOGIN", "RESET_PASSWORD", "DELETE_WORK", "CHANGE_PHONE_OLD", "CHANGE_EMAIL_NEW").contains(scene))
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "invalid scene");
        String normalized = normalizeEmail(email);
        sendCodeInternal("email:" + normalized, "EMAIL_" + scene, ip, requestId, normalized);
    }

    public void consumeEmailCode(String email, String code)
    {
        consumeEmailCode(email, "REGISTER", code);
    }

    public void consumeEmailCode(String email, String scene, String code)
    {
        consumeOnce("email:" + normalizeEmail(email), "EMAIL_" + scene, code);
    }

    private final AppAuthProperties properties;
    private final AppSmsCodeMapper mapper;
    private final SmsProvider smsProvider;
    private final RedisCache redisCache;
    private final AppSessionRevocationService revocationService;
    private final AppSecurityEventRecorder securityEvents;
    /** REQUIRES_NEW 语义：错误次数增量必须独立于调用方事务提交（AUTH-04 失败锁定）。 */
    private final TransactionTemplate failedAttemptTx;

    public SmsCodeService(AppAuthProperties properties,
            AppSmsCodeMapper mapper,
            SmsProvider smsProvider,
            RedisCache redisCache,
            AppSessionRevocationService revocationService,
            AppSecurityEventRecorder securityEvents,
            PlatformTransactionManager transactionManager)
    {
        this.properties = properties;
        this.mapper = mapper;
        this.smsProvider = smsProvider;
        this.redisCache = redisCache;
        this.revocationService = revocationService;
        this.securityEvents = securityEvents;
        // consumeOnce 在 register/smsLogin 等外层事务里被调用；失败计数若随外层
        // 事务回滚，max-failed-attempts 锁定就永远不生效，故增量走独立事务提交。
        this.failedAttemptTx = new TransactionTemplate(transactionManager);
        this.failedAttemptTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /** AUTH-06: frequency-limit decisions must leave a security-audit record. */
    private void recordSecurityEvent(String phone, String ip, String event, int observed)
    {
        if (securityEvents != null)
        {
            securityEvents.record(phone.startsWith("email:") ? "***@" + phone.substring(phone.indexOf('@') + 1) : AppHashes.maskPhone(phone), ip, event,
                    "scene limit observed=" + observed);
        }
    }

    public void sendCode(String phone, String scene, String ip, String requestId)
    {
        if (phone == null || !phone.matches("^1\\d{10}$"))
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "invalid phone");
        }
        if (scene != null && scene.startsWith("EMAIL_"))
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "invalid scene");
        sendCodeInternal(phone, scene, ip, requestId, null);
    }

    private void sendCodeInternal(String phone, String scene, String ip, String requestId, String email)
    {
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
        if (email == null && smsProvider instanceof MockSmsProvider mock)
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
        if (email == null) smsProvider.send(phone, scene, code);
        else if (emailSender != null) emailSender.send(email, code, properties.getSms().getCodeTtlSeconds());
        else throw new AppAuthException(AppAuthErrorCodes.SYSTEM_ERROR, 503, "邮箱验证码服务暂不可用");
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
        if (latest.getExpiresAt() == null || !latest.getExpiresAt().after(now))
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
            failedAttemptTx.execute(status -> mapper.incrementFailed(latest.getId()));
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
