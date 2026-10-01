package com.smartscript.platform.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;
import com.smartscript.platform.user.exception.AppAdminException;

import java.util.concurrent.TimeUnit;

/**
 * A4 敏感材料/附件的短时授权引用（契约 §3.2 / §3.5 / §7）。
 *
 * 设计要点：
 *   1. **不向客户端暴露永久地址**。对外只返回不透明令牌（base64url 随机串），
 *      真实引用只存在于服务端存储中，客户端无法从令牌反推或拼接出材料地址。
 *   2. **过期由服务端强制**。令牌以 TTL 写入缓存，到期即失效；
 *      不存在「客户端自己改 exp 参数」这类可绕过的做法。
 *   3. **一次性消费**：兑换后立即删除，避免令牌被转发或重放。
 *   4. 兑换必须持有对应详情权限，且由调用方记录访问审计。
 *
 * 与最终材料网关的边界：本服务提供可强制的短时授权语义与兑换端点。
 * 若后续 A5 接入真实对象存储签名 URL，只需在 {@link #redeem} 内改为
 * 向网关换取签名地址即可，对外令牌格式与调用方无需变化。
 */
@Service
public class MaterialAccessTokenService
{
    private static final Logger log = LoggerFactory.getLogger(MaterialAccessTokenService.class);

    /** 令牌有效期（秒）。契约要求「短时有效」。 */
    public static final long TOKEN_TTL_SECONDS = 300L;

    /** 材料类型：实名材料。 */
    public static final String KIND_REAL_NAME = "REAL_NAME_MATERIAL";

    /** 材料类型：反馈附件。 */
    public static final String KIND_FEEDBACK = "FEEDBACK_ATTACHMENT";

    private static final String KEY_PREFIX = "a4:material-ref:";

    /** 存储格式分隔符：材料类型 + 换行 + 真实引用。 */
    private static final String SEPARATOR = "\n";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 兑换脚本：原子地取出并删除令牌。
     *
     * 先 GET 再 DEL 不是原子操作——并发请求可以同时 GET 成功，然后都返回真实地址。
     * Lua 脚本在 Redis 内单线程执行，读取与删除之间不存在窗口。
     */
    private static final String REDEEM_SCRIPT =
            "local v = redis.call('GET', KEYS[1]) "
          + "if v == false then return nil end "
          + "redis.call('DEL', KEYS[1]) "
          + "return v";

    private final RedisTemplate<String, String> stringRedis;
    private final DefaultRedisScript<Object> redeemScript;

    public MaterialAccessTokenService(RedisConnectionFactory connectionFactory)
    {
        // 本地构建专用 String 模板，而不是注册成 Bean：
        // 容器里已有 RedisTemplate<Object,Object> 与自动配置的 stringRedisTemplate，
        // 再加同类型 Bean 会让既有按类型注入失败。
        this.stringRedis = new RedisTemplate<>();
        this.stringRedis.setConnectionFactory(connectionFactory);
        this.stringRedis.setKeySerializer(new StringRedisSerializer());
        this.stringRedis.setValueSerializer(new StringRedisSerializer());
        this.stringRedis.afterPropertiesSet();
        this.redeemScript = new DefaultRedisScript<>(REDEEM_SCRIPT, Object.class);
    }

    /**
     * 为真实引用签发短时令牌。
     *
     * @param kind  材料类型（{@link #KIND_REAL_NAME} / {@link #KIND_FEEDBACK}）
     * @param rawRef 数据库中的真实引用；为空时返回 null（表示无材料）
     * @return 不透明令牌；调用方把它作为 materialRefs / attachmentRefs 的元素返回
     */
    public String issue(String kind, String rawRef)
    {
        if (rawRef == null || rawRef.isBlank())
        {
            return null;
        }
        String resolvedKind = (kind == null || kind.isBlank()) ? "UNKNOWN" : kind;
        String token = newToken();
        // 类型与引用一起存储：兑换时据此校验调用方权限与材料类型是否匹配
        // 以 JSON 字符串写入：template 声明为 RedisTemplate<Object, Object>，
        // 直接写 String 会让 FastJSON 序列化器丢失类型信息，读取时得到 null。
        stringRedis.opsForValue().set(KEY_PREFIX + token, resolvedKind + SEPARATOR + rawRef,
                TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
        // 日志只记录不可逆指纹，不记录令牌与真实地址
        log.debug("a4-material token issued kind={} fp={}", resolvedKind, fingerprint(rawRef));
        return token;
    }

    /**
     * 兑换结果：材料类型 + 服务端持有的真实引用。
     *
     * 真实引用**不返回给调用方**，只交给紧随其后的材料网关换签；
     * 这样 HTTP 响应与操作日志都不可能带上永久地址。
     */
    public static class RedeemResult
    {
        private final String kind;
        private final String rawRef;

        public RedeemResult(String kind, String rawRef)
        {
            this.kind = kind;
            this.rawRef = rawRef;
        }

        public String getKind()
        {
            return kind;
        }

        /** 仅供服务端内部使用（网关换签）；禁止序列化进响应或日志。 */
        public String getRawRefInternal()
        {
            return rawRef;
        }
    }

    /**
     * 原子兑换令牌并校验材料类型与调用方权限匹配。
     *
     * @param token       客户端提交的令牌
     * @param grantedPerms 调用方实际持有的权限集合
     * @return 兑换结果（含材料类型）；令牌不可兑换时返回 null
     *
     * 令牌在一次操作内取出并删除，因此并发请求中只有一个能得到结果；
     * 类型与所持权限不匹配时同样返回 null，不区分原因以免探测。
     * 调用方不得把 rawRef 写入响应或日志。
     */
    public RedeemResult tryRedeem(String token, List<String> grantedPerms)
    {
        if (!looksLikeToken(token))
        {
            return null;
        }

        String stored = redeemAtomically(token);
        if (stored == null)
        {
            // 过期、伪造，或已被并发请求抢先消费
            return null;
        }

        int sep = stored.indexOf(SEPARATOR);
        if (sep <= 0)
        {
            log.warn("a4-material token payload malformed fp={}", fingerprint(stored));
            return null;
        }
        String kind = stored.substring(0, sep);
        String rawRef = stored.substring(sep + 1);

        // 令牌绑定的材料类型必须落在调用方所持权限的范围内
        String requiredPerm = permissionForKind(kind);
        boolean permitted = requiredPerm != null && grantedPerms != null
                && (grantedPerms.contains(requiredPerm) || grantedPerms.contains("*:*:*"));
        if (!permitted)
        {
            log.debug("a4-material redeem denied kind={} (permission mismatch)", kind);
            return null;
        }

        log.debug("a4-material token redeemed kind={} fp={}", kind, fingerprint(rawRef));
        return new RedeemResult(kind, rawRef);
    }

    /** 材料类型 → 兑换所需权限。 */
    public static String permissionForKind(String kind)
    {
        if (KIND_REAL_NAME.equals(kind))
        {
            return com.smartscript.platform.user.constant.AppAdminConstants.PERM_REALNAME_QUERY;
        }
        if (KIND_FEEDBACK.equals(kind))
        {
            return com.smartscript.platform.user.constant.AppAdminConstants.PERM_FEEDBACK_QUERY;
        }
        return null;
    }

    /**
     * 用 Lua 脚本原子取出并删除令牌。
     *
     * RedisCache 的 template 声明为 RedisTemplate<Object, Object>，
     * 这里显式以 String 反序列化：写入的是字符串（非 JSON 对象），
     * 避免 FastJson 反序列化器把 KV 读成 Map 导致类型不确定。
     */
    @SuppressWarnings("unchecked")
    private String redeemAtomically(String token)
    {
        try
        {
            // Lua 返回的是原始字节；用 Object.class 接收后按 UTF-8 转字符串，
            // 若声明为 String.class，Spring 的类型转换会得到 null（链接被误判为不存在）。
            Object raw = stringRedis.execute(redeemScript, Collections.singletonList(KEY_PREFIX + token));
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
            // 脚本异常绝不降级为放行；按不存在处理并留痕
            log.warn("a4-material redeem script failed: {}", e.getClass().getSimpleName());
            throw AppAdminException.notFound();
        }
    }

    /** 令牌形态校验，用于在授权前拒绝明显非法的输入。 */
    public static boolean looksLikeToken(String token)
    {
        return token != null && token.matches("[A-Za-z0-9_-]{22,128}");
    }

    private String newToken()
    {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 真实引用的不可逆指纹，仅用于日志与排障，不泄露地址本身。 */
    private String fingerprint(String rawRef)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawRef.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(12);
            for (int i = 0; i < 6; i++)
            {
                hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            return "unavailable";
        }
    }
}
