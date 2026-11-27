package com.smartscript.platform.user.service;

import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.dto.QrLoginTicket;
import com.smartscript.platform.user.util.AppHashes;

/**
 * 扫码登录票据的 Redis 读写。
 *
 * <p>值以 JSON 字符串存放，而不是把 DTO 直接交给公共 {@code RedisTemplate}：
 * 该模板用 FastJson2 写入类型信息，而反序列化白名单只放行 {@code com.ruoyi} 包，
 * 本模块的类型会被拒绝。存字符串既避开这个限制，也让键值在 redis-cli 里可读。</p>
 *
 * <p>键里只出现票据明文的 SHA-256：二维码可能被截图、被日志记录，
 * 但 Redis 里没有任何可以直接拿来轮询的明文。</p>
 */
@Service
public class QrLoginTickets
{
    private static final String TICKET_PREFIX = "qr-login:ticket:";
    private static final String POLL_PREFIX = "qr-login:poll:";

    private final RedisCache redisCache;
    private final AppAuthProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QrLoginTickets(RedisCache redisCache, AppAuthProperties properties)
    {
        this.redisCache = redisCache;
        this.properties = properties;
    }

    public void save(String qrToken, QrLoginTicket ticket, int ttlSeconds)
    {
        redisCache.setCacheObject(ticketKey(qrToken), write(ticket), ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 只推进状态、不续命：按剩余有效期写回。
     *
     * <p>剩余时间在这里读到 0 就直接视为失效，调用方据此报错。
     * 读剩余时间到写回之间若票据刚好过期，最坏结果是写回一条存活 ≤ 原剩余时长的旧票，
     * 不会延长票据寿命，也不会越过其原始截止时间。</p>
     */
    public boolean savePreservingTtl(String qrToken, QrLoginTicket ticket)
    {
        String key = ticketKey(qrToken);
        long remaining = redisCache.getExpire(key);
        if (remaining <= 0)
        {
            return false;
        }
        redisCache.setCacheObject(key, write(ticket), (int) remaining, TimeUnit.SECONDS);
        return true;
    }

    public QrLoginTicket get(String qrToken)
    {
        Object raw = redisCache.getCacheObject(ticketKey(qrToken));
        if (raw == null)
        {
            return null;
        }
        try
        {
            return objectMapper.readValue(String.valueOf(raw), QrLoginTicket.class);
        }
        catch (JsonProcessingException e)
        {
            // 值损坏等同于票据无效，删掉避免每次轮询都撞同一条坏数据。
            redisCache.deleteObject(ticketKey(qrToken));
            return null;
        }
    }

    /** 原子取走票据（GETDEL）：并发轮询里只有一个调用方拿到非空值。 */
    public QrLoginTicket take(String qrToken)
    {
        Object raw = redisCache.redisTemplate.opsForValue().getAndDelete(ticketKey(qrToken));
        if (raw == null)
        {
            return null;
        }
        try
        {
            return objectMapper.readValue(String.valueOf(raw), QrLoginTicket.class);
        }
        catch (JsonProcessingException e)
        {
            return null;
        }
    }

    public void delete(String qrToken)
    {
        redisCache.deleteObject(ticketKey(qrToken));
    }

    /**
     * 轮询限频：同一票据在 1 秒内只允许一次轮询。
     *
     * <p>正常情况下 PC 按服务端下发的间隔（默认 2 秒）轮询，不会触发；
     * 触发的多半是有人拿着票据id在扫描节奏。返回 false 时调用方回 429。</p>
     */
    public boolean acquirePollSlot(String qrToken)
    {
        String key = pollKey(qrToken);
        Boolean acquired = redisCache.redisTemplate.opsForValue().setIfAbsent(key, "1", 1, TimeUnit.SECONDS);
        return !Boolean.FALSE.equals(acquired);
    }

    private String ticketKey(String qrToken)
    {
        return base() + TICKET_PREFIX + AppHashes.sha256Hex(qrToken);
    }

    private String pollKey(String qrToken)
    {
        return base() + POLL_PREFIX + AppHashes.sha256Hex(qrToken);
    }

    private String base()
    {
        String prefix = properties.getRedisKeyPrefix();
        return prefix.endsWith(":") ? prefix : prefix + ":";
    }

    private String write(QrLoginTicket ticket)
    {
        try
        {
            return objectMapper.writeValueAsString(ticket);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalStateException("扫码登录票据序列化失败", e);
        }
    }
}
