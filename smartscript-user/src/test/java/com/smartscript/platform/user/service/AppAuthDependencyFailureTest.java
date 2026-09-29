package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppSmsCode;
import com.smartscript.platform.user.domain.UserFeedback;
import com.smartscript.platform.user.dto.FeedbackCreateRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.integration.SmsProvider;
import com.smartscript.platform.user.mapper.AppSmsCodeMapper;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;

/**
 * H-12 稳定性：Redis 依赖失效时的行为口径（第 5 批加固新增）。
 *
 * 固定两类行为，防止后续无意改口径：
 *   1. 短信发码在 Redis 不可用时**失败关闭**（异常向上抛出）——冷却/幂等键读写是发码前置条件。
 *      运行时还观察到更前置的约束：RuoYi 的 SysConfigServiceImpl 在启动时会预热配置缓存，
 *      Redis 不可达将导致应用根本无法启动（见第 5 批记录 §6.3）。
 *   2. 反馈提交在 Redis 抛异常时**失败开放**（A5-05 设计：频控是防滥用措施，非正确性前提），
 *      但在 Redis 正常且计数达上限时仍必须返回 429。
 *   3. 短信 requestId 幂等仅在 Redis 键存活期内有效——键存在时吞掉重复请求，键缺失时放行。
 *
 * 说明：本测试用最小化假实现（动态代理 / 匿名子类），不引入真实 Redis 连接。
 */
class AppAuthDependencyFailureTest
{
    private static final String REDIS_DOWN = "redis unavailable (test stub)";

    // ---------------------------------------------------------------- 假实现

    /** 任何键读取都抛异常，模拟 Redis 不可达。 */
    private static class DownRedisCache extends com.ruoyi.common.core.redis.RedisCache
    {
        @Override
        public <T> T getCacheObject(String key)
        {
            throw new IllegalStateException(REDIS_DOWN);
        }

        @Override
        public <T> void setCacheObject(String key, T value, Integer timeout, java.util.concurrent.TimeUnit unit)
        {
            throw new IllegalStateException(REDIS_DOWN);
        }
    }

    /** 键读取返回指定计数值，写入为空操作（模拟 Redis 正常）。 */
    private static class CountingRedisCache extends com.ruoyi.common.core.redis.RedisCache
    {
        private final Object current;

        CountingRedisCache(Object current)
        {
            this.current = current;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCacheObject(String key)
        {
            return (T) current;
        }

        @Override
        public <T> void setCacheObject(String key, T value, Integer timeout, java.util.concurrent.TimeUnit unit)
        {
            // no-op
        }
    }

    /** 只实现 setIfAbsent 的 RedisTemplate/ValueOperations，其余返回类型默认值。 */
    private static RedisTemplate<String, String> templateWithSetIfAbsent(Boolean result)
    {
        ValueOperations<String, String> ops = (ValueOperations<String, String>) Proxy.newProxyInstance(
                AppAuthDependencyFailureTest.class.getClassLoader(),
                new Class<?>[] { ValueOperations.class },
                (proxy, method, args) ->
                {
                    if ("setIfAbsent".equals(method.getName()))
                    {
                        return result;
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return false;
                    if (rt == long.class) return 0L;
                    return null;
                });
        return new RedisTemplate<>()
        {
            @Override
            public ValueOperations<String, String> opsForValue()
            {
                return ops;
            }
        };
    }

    /** 记录发码调用的 provider 假实现。 */
    private static class RecordingProvider implements SmsProvider
    {
        final List<String> sent = new ArrayList<>();

        @Override
        public void send(String phone, String scene, String code)
        {
            sent.add(scene);
        }
    }

    /** 计数固定返回 0 的短信 mapper（不触发频控）。 */
    private static class ZeroCountMapper implements AppSmsCodeMapper
    {
        int inserts;

        @Override
        public int insertSmsCode(AppSmsCode record)
        {
            inserts++;
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
            return 1;
        }

        @Override
        public int incrementFailed(Long id)
        {
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

    /** 只关心 insertFeedback 的用户中心 mapper。 */
    private static AppUserCenterMapper feedbackMapper(List<UserFeedback> sink)
    {
        return (AppUserCenterMapper) Proxy.newProxyInstance(
                AppAuthDependencyFailureTest.class.getClassLoader(),
                new Class<?>[] { AppUserCenterMapper.class },
                (proxy, method, args) ->
                {
                    if ("insertFeedback".equals(method.getName()))
                    {
                        UserFeedback f = (UserFeedback) args[0];
                        f.setFeedbackId(9001L);
                        sink.add(f);
                        return 1;
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == int.class) return 0;
                    if (rt == java.util.List.class) return new ArrayList<>();
                    return null;
                });
    }

    private static AppSessionRevocationService keyOnlyRevocation()
    {
        return new AppSessionRevocationService(new AppAuthProperties(), null, null);
    }

    /** 构造一个键读取正常、setIfAbsent 结果可控的 RedisCache（直接设置 public redisTemplate 字段）。 */
    @SuppressWarnings("unchecked")
    private static com.ruoyi.common.core.redis.RedisCache redisCacheWithSetIfAbsent(Boolean setIfAbsent)
    {
        CountingRedisCache cache = new CountingRedisCache(null);
        RedisTemplate<String, String> template = templateWithSetIfAbsent(setIfAbsent);
        cache.redisTemplate = template;
        return cache;
    }

    private static FeedbackCreateRequest feedbackRequest(String content)
    {
        FeedbackCreateRequest req = new FeedbackCreateRequest();
        req.setCategory("BUG");
        req.setContent(content);
        return req;
    }

    // ---------------------------------------------------------------- 断言

    @Test
    void smsSendFailsClosedWhenRedisUnavailable()
    {
        ZeroCountMapper mapper = new ZeroCountMapper();
        RecordingProvider provider = new RecordingProvider();
        SmsCodeService service = new SmsCodeService(new AppAuthProperties(), mapper, provider,
                new DownRedisCache(), keyOnlyRevocation(), new AppSecurityEventRecorder(null));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.sendCode("13900000001", "LOGIN", "127.0.0.1", "req-down"));
        assertTrue(ex.getMessage() == null || ex.getMessage().contains("redis"),
                "Redis 异常应原样抛出（失败关闭），实际：" + ex);
        assertEquals(0, mapper.inserts, "Redis 不可用时不得落库发码记录");
        assertTrue(provider.sent.isEmpty(), "Redis 不可用时不得调用短信 Provider");
    }

    @Test
    void feedbackSubmitFailsOpenWhenRedisUnavailable()
    {
        List<UserFeedback> sink = new ArrayList<>();
        UserFeedbackService service = new UserFeedbackService(
                feedbackMapper(sink), new DownRedisCache(), keyOnlyRevocation());

        assertDoesNotThrow(() -> service.create(77L, feedbackRequest("Redis 不可达时提交反馈。")),
                "A5-05：Redis 异常不得阻断反馈写入（失败开放）");
        assertEquals(1, sink.size(), "失败开放路径仍应写入且只写一条");
        assertEquals(77L, sink.get(0).getUserId());
    }

    @Test
    void feedbackRateLimitStillEnforcedWhenRedisCounterAtLimit()
    {
        List<UserFeedback> sink = new ArrayList<>();
        UserFeedbackService service = new UserFeedbackService(
                feedbackMapper(sink), new CountingRedisCache(5), keyOnlyRevocation());

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> service.create(78L, feedbackRequest("达到小时上限后的提交。")));
        assertEquals(AppAuthErrorCodes.SMS_RATE_LIMIT, ex.getCode());
        assertEquals(429, ex.getHttpStatus());
        assertTrue(sink.isEmpty(), "被频控拒绝时不得写库");
    }

    @Test
    void smsRequestIdIdempotentSwallowsDuplicateWhenKeyExists()
    {
        ZeroCountMapper mapper = new ZeroCountMapper();
        RecordingProvider provider = new RecordingProvider();
        // setIfAbsent 返回 FALSE 表示键已存在 -> 应被幂等吞掉
        SmsCodeService service = new SmsCodeService(new AppAuthProperties(), mapper, provider,
                redisCacheWithSetIfAbsent(Boolean.FALSE), keyOnlyRevocation(), new AppSecurityEventRecorder(null));

        assertDoesNotThrow(() -> service.sendCode("13900000003", "LOGIN", "127.0.0.1", "req-dup"));
        assertEquals(0, mapper.inserts, "同 requestId 重复请求不得新增发码记录");
        assertTrue(provider.sent.isEmpty(), "同 requestId 重复请求不得再次调用 Provider");
    }

    @Test
    void smsRequestIdResendAllowedWhenKeyAbsent()
    {
        ZeroCountMapper mapper = new ZeroCountMapper();
        RecordingProvider provider = new RecordingProvider();
        // setIfAbsent 返回 TRUE 表示键不存在 -> 放行；这也说明幂等仅依赖 Redis 键存活
        SmsCodeService service = new SmsCodeService(new AppAuthProperties(), mapper, provider,
                redisCacheWithSetIfAbsent(Boolean.TRUE), keyOnlyRevocation(), new AppSecurityEventRecorder(null));

        assertDoesNotThrow(() -> service.sendCode("13900000004", "LOGIN", "127.0.0.1", "req-fresh"));
        assertEquals(1, mapper.inserts, "键缺失时放行并落库（幂等非持久，仅 Redis 存活期内有效）");
        assertEquals(1, provider.sent.size());
    }
}
