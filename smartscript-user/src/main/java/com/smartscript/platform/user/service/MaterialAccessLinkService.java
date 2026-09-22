package com.smartscript.platform.user.service;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;
import com.smartscript.platform.user.exception.AppAdminException;

/**
 * A4 材料访问链接（短时 + 一次性）。
 *
 * 与 {@link MaterialAccessTokenService} 的分工：
 *   - 前者是「详情响应 → 兑换」这一步的令牌，绑定材料类型与权限，兑换后得到材料引用；
 *   - 本服务是「材料引用 → 真实内容」这一步的访问链接，由服务端生成 URL，
 *     页面拿到 URL 后直接请求即可读到字节流。
 *
 * 安全属性：
 *   1. **短时**：有效期由服务端强制（Redis TTL），客户端无法延长。
 *   2. **一次性**：取内容时用 Lua 脚本原子取出并删除；并发只能成功一次。
 *   3. **地址不可猜测**：随机 32 字节 base64url，不包含存储路径。
 *   4. **不泄露引用**：链接中不含材料引用；引用只存在服务端存储里。
 *   5. 链接不得写入日志（调用方排除参数与响应体）。
 */
@Service
public class MaterialAccessLinkService
{
    private static final Logger log = LoggerFactory.getLogger(MaterialAccessLinkService.class);

    /** 访问链接有效期（秒）。短到足以限制转发，长到足够页面完成一次查看。 */
    public static final long LINK_TTL_SECONDS = 120L;

    private static final String KEY_PREFIX = "a4:material-link:";

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** 存储格式：材料类型 + 换行 + 真实引用。类型仅用于审计与权限复核。 */
    private static final String SEPARATOR = "\n";

    /** 原子取出并删除，保证一次性。 */
    private static final String CONSUME_SCRIPT =
            "local v = redis.call('GET', KEYS[1]) "
          + "if v == false then return nil end "
          + "redis.call('DEL', KEYS[1]) "
          + "return v";

    private final RedisTemplate<String, String> stringRedis;
    private final DefaultRedisScript<Object> consumeScript;

    public MaterialAccessLinkService(RedisConnectionFactory connectionFactory)
    {
        // 本地构建专用 String 模板，而不是注册成 Bean：
        // 容器里已有 RedisTemplate<Object,Object> 与自动配置的 stringRedisTemplate，
        // 再加同类型 Bean 会让既有按类型注入失败。
        this.stringRedis = new RedisTemplate<>();
        this.stringRedis.setConnectionFactory(connectionFactory);
        this.stringRedis.setKeySerializer(new StringRedisSerializer());
        this.stringRedis.setValueSerializer(new StringRedisSerializer());
        this.stringRedis.afterPropertiesSet();
        this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, Object.class);
    }

    /**
     * 为材料引用签发一次性访问链接。
     *
     * @param kind   材料类型（复用 {@link MaterialAccessTokenService} 的常量）
     * @param rawRef 真实材料引用
     * @return 相对访问路径（不含域名），客户端请求该路径即可取回内容
     */
    public String issue(String kind, String rawRef)
    {
        if (rawRef == null || rawRef.isBlank())
        {
            throw AppAdminException.notFound();
        }
        String token = newToken();
        // 以 JSON 字符串写入：template 声明为 RedisTemplate<Object, Object>，
        // 直接写 String 会让 FastJSON 序列化器丢失类型信息，读取时得到 null。
        stringRedis.opsForValue().set(KEY_PREFIX + token, kind + SEPARATOR + rawRef,
                LINK_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("a4-material access link issued kind={}", kind);
        return "/api/v1/admin/material/content/" + token;
    }

    /**
     * 消费访问链接，返回材料类型与引用。
     *
     * 链接不存在、过期或已被消费时返回 null，调用方按不存在处理。
     */
    public LinkPayload consume(String token)
    {
        if (token == null || !token.matches("[A-Za-z0-9_-]{22,128}"))
        {
            return null;
        }
        String stored = consumeAtomically(token);
        if (stored == null)
        {
            return null;
        }
        int sep = stored.indexOf(SEPARATOR);
        if (sep <= 0)
        {
            log.warn("a4-material access link payload malformed");
            return null;
        }
        return new LinkPayload(stored.substring(0, sep), stored.substring(sep + 1));
    }

    @SuppressWarnings("unchecked")
    private String consumeAtomically(String token)
    {
        try
        {
            // Lua 返回的是原始字节；用 Object.class 接收后按 UTF-8 转字符串，
            // 若声明为 String.class，Spring 的类型转换会得到 null（链接被误判为不存在）。
            Object raw = stringRedis.execute(consumeScript, Collections.singletonList(KEY_PREFIX + token));
            if (raw == null)
            {
                return null;
            }
            if (raw instanceof byte[] bytes)
            {
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
            return String.valueOf(raw);
        }
        catch (RuntimeException e)
        {
            log.warn("a4-material link consume failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String newToken()
    {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 链接载荷：材料类型 + 真实引用。引用不得进入响应体或日志。 */
    public static class LinkPayload
    {
        private final String kind;
        private final String rawRef;

        public LinkPayload(String kind, String rawRef)
        {
            this.kind = kind;
            this.rawRef = rawRef;
        }

        public String getKind()
        {
            return kind;
        }

        /** 仅供服务端读取文件使用；禁止序列化。 */
        public String getRawRefInternal()
        {
            return rawRef;
        }
    }

    /** 供调用方列出允许的类型，避免误传。 */
    public static List<String> supportedKinds()
    {
        return List.of(MaterialAccessTokenService.KIND_REAL_NAME,
                MaterialAccessTokenService.KIND_FEEDBACK);
    }
}
