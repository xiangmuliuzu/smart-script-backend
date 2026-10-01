package com.smartscript.platform.user.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A5 一次性 step-up 凭证存储（契约 §1.4）。
 *
 * 用于换绑手机号第一步（验证旧号）与第三步（确认换绑）之间的短时授权：
 *   - 凭证绑定 userId + deviceId + 场景，换设备或跨用户不可用；
 *   - 有效期由服务端强制（默认 600 秒），客户端无法延长；
 *   - **一次性**：兑换即删除，用 Lua 脚本原子「取出并删除」，并发只有一次成功；
 *   - 只存于 Redis，不落库、不写日志、不进响应以外的任何位置。
 *
 * 与 A4 {@code MaterialAccessTokenService} 的差异：该凭证不出域给外部消费者，
 * 仅在本服务内闭环使用，因此不做材料类型权限匹配。
 */
@Component
public class PhoneChangeTokenStore
{
    private static final Logger log = LoggerFactory.getLogger(PhoneChangeTokenStore.class);

    /** 凭证有效期（秒）。规格要求「短时」。 */
    public static final long TTL_SECONDS = 600L;

    /** 凭证值格式：userId + 换行 + scene + 换行 + deviceId。 */
    private static final String SEPARATOR = "\n";

    /** 换绑场景标识，避免与其它 step-up 用途共用凭证。 */
    public static final String SCENE_PHONE_CHANGE = "PHONE_CHANGE";

    static final String KEY_SUFFIX = "phone:step-up:";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 原子取出并删除。先 GET 再 DEL 不是原子操作，并发请求会同时读到同一凭证。
     */
    private static final String CONSUME_SCRIPT =
            "local v = redis.call('GET', KEYS[1]) "
          + "if v == false then return nil end "
          + "redis.call('DEL', KEYS[1]) "
          + "return v";

    private final RedisTemplate<String, String> stringRedis;
    private final DefaultRedisScript<Object> consumeScript;
    private final AppSessionRevocationService revocationService;

    public PhoneChangeTokenStore(RedisConnectionFactory connectionFactory,
            AppSessionRevocationService revocationService)
    {
        // 本地构建 String 模板而非注册 Bean：容器已有 RedisTemplate<Object,Object>
        // 与自动配置的 stringRedisTemplate，再加同类型 Bean 会让既有按类型注入失败。
        this.stringRedis = new RedisTemplate<>();
        this.stringRedis.setConnectionFactory(connectionFactory);
        this.stringRedis.setKeySerializer(new StringRedisSerializer());
        this.stringRedis.setValueSerializer(new StringRedisSerializer());
        this.stringRedis.afterPropertiesSet();
        this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, Object.class);
        this.revocationService = revocationService;
    }

    /**
     * 签发一次性凭证。
     *
     * @return 不透明凭证串；调用方只把该串返回给发起换绑的客户端
     */
    public String issue(Long userId, String deviceId)
    {
        String token = newToken();
        String payload = String.valueOf(userId) + SEPARATOR + SCENE_PHONE_CHANGE + SEPARATOR + safe(deviceId);
        stringRedis.opsForValue().set(keyFor(token), payload, TTL_SECONDS, TimeUnit.SECONDS);
        // 日志只记录凭证指纹与用户，不记录凭证本身
        log.debug("a5-phone step-up issued userId={} ttl={}", userId, TTL_SECONDS);
        return token;
    }

    /**
     * 原子消费凭证并校验绑定关系。
     *
     * @param token    客户端提交的凭证
     * @param userId   当前登录用户，必须与签发时一致
     * @param deviceId 当前设备，必须与签发时一致（均为空视为一致，兼容未上报设备的客户端）
     * @return 校验通过返回 true；凭证不存在、已消费、过期或绑定不符返回 false
     */
    public boolean consume(String token, Long userId, String deviceId)
    {
        if (!looksLikeToken(token) || userId == null)
        {
            return false;
        }
        String stored = consumeAtomically(token);
        if (stored == null)
        {
            return false;
        }
        String[] parts = stored.split(SEPARATOR, -1);
        if (parts.length != 3)
        {
            log.warn("a5-phone step-up payload malformed");
            return false;
        }
        if (!SCENE_PHONE_CHANGE.equals(parts[1]))
        {
            return false;
        }
        if (!String.valueOf(userId).equals(parts[0]))
        {
            // 凭证被转发给其它用户：按不可用处理，不再复用
            log.warn("a5-phone step-up userId mismatch");
            return false;
        }
        String boundDevice = parts[2];
        String currentDevice = safe(deviceId);
        if (!boundDevice.isEmpty() && !currentDevice.isEmpty() && !boundDevice.equals(currentDevice))
        {
            log.warn("a5-phone step-up device mismatch");
            return false;
        }
        return true;
    }

    /** 凭证形态校验：在触碰存储前拒绝明显非法输入。 */
    public static boolean looksLikeToken(String token)
    {
        return token != null && token.matches("[A-Za-z0-9_-]{22,128}");
    }

    @SuppressWarnings("unchecked")
    private String consumeAtomically(String token)
    {
        try
        {
            // Lua 返回原始字节；声明为 String.class 会得到 null，故用 Object 接收后按 UTF-8 转
            Object raw = stringRedis.execute(consumeScript, Collections.singletonList(keyFor(token)));
            if (raw == null)
            {
                return null;
            }
            if (raw instanceof byte[] bytes)
            {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            return String.valueOf(raw);
        }
        catch (RuntimeException e)
        {
            // 脚本异常不得降级为放行
            log.warn("a5-phone step-up consume failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String keyFor(String token)
    {
        // 复用 App 认证域的 key 前缀，保证多环境/多套件之间凭证隔离
        return revocationService.key(KEY_SUFFIX + token);
    }

    private static String newToken()
    {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }
}
