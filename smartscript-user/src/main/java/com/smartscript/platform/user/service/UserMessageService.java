package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.UserMessage;
import com.smartscript.platform.user.domain.UserNotificationPreference;
import com.smartscript.platform.user.dto.NotificationPreferenceDto;
import com.smartscript.platform.user.dto.NotificationPreferenceUpdateRequest;
import com.smartscript.platform.user.dto.PageResult;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;

/**
 * A5 消息中心与通知偏好（契约 §1.5，规格 §8.6）。
 *
 * 强制边界：
 *   - 站内消息按用户收件记录读取，数据归属在 SQL 层限定，他人消息查不到行。
 *   - 已读操作幂等：重复标记已读不报错、不改动既有 read_at。
 *   - 关闭推送（PUSH）不等于删除站内消息：两者是独立的 渠道+类型 组合，
 *     站内消息仍照常投递（规格 §8.6 末句）。
 *   - 偏好采用稀疏存储：只有与默认值不同的组合落库，其余由默认值展开，
 *     因此新增消息类型时不会因缺少历史行而丢失默认开启语义。
 */
@Service
public class UserMessageService
{
    /** 通知渠道：站内信。 */
    public static final String CHANNEL_INBOX = "INBOX";
    /** 通知渠道：推送。 */
    public static final String CHANNEL_PUSH = "PUSH";

    /** 支持的渠道集合（契约 §1.5）。 */
    private static final List<String> CHANNELS = List.of(CHANNEL_INBOX, CHANNEL_PUSH);

    /** 支持的消息类型集合，与 A4 消息创建端 AppAdminConstants 同集合。 */
    private static final List<String> TYPES = List.of(
            AppAdminConstants.NOTIFICATION_SYSTEM,
            AppAdminConstants.NOTIFICATION_REVIEW,
            AppAdminConstants.NOTIFICATION_TRANSACTION,
            AppAdminConstants.NOTIFICATION_BENEFIT);

    /** 列表摘要长度：列表只展示摘要，正文在详情返回。 */
    private static final int SUMMARY_LENGTH = 80;

    private final AppUserCenterMapper centerMapper;

    public UserMessageService(AppUserCenterMapper centerMapper)
    {
        this.centerMapper = centerMapper;
    }

    /**
     * 我的消息分页。分页由本方法内的 PageHelper 驱动。
     * 列表不返回正文，只返回摘要，避免一次拉取大量长文本。
     *
     * 必须在这里取 total：映射到摘要 Map 之后 PageInfo 已退化为普通列表，
     * 那时再取 total 只能得到当前页条数。
     */
    public PageResult<Map<String, Object>> page(Long userId, String type, int pageNum, int pageSize)
    {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", userId);
        params.put("type", normalizeType(type, true));
        PageHelper.startPage(pageNum, pageSize);
        List<UserMessage> rows = centerMapper.selectMyMessages(params);
        long total = new PageInfo<>(rows).getTotal();
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserMessage row : rows)
        {
            list.add(toSummary(row));
        }
        return PageResult.of(total, list);
    }

    /** 未读数：总数 + 按类型分布，供角标与筛选标签使用。 */
    public Map<String, Object> unreadCount(Long userId)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", centerMapper.countUnread(userId));
        Map<String, Integer> byType = new TreeMap<>();
        List<Map<String, Object>> rows = centerMapper.countUnreadGroupByType(userId);
        if (rows != null)
        {
            for (Map<String, Object> row : rows)
            {
                Object type = row.get("type");
                Object total = row.get("total");
                if (type == null || total == null)
                {
                    continue;
                }
                byType.put(String.valueOf(type), ((Number) total).intValue());
            }
        }
        result.put("byType", byType);
        return result;
    }

    /** 详情（含正文）；归属不符与不存在同样返回 404。 */
    public Map<String, Object> detail(Long userId, Long messageId)
    {
        if (messageId == null)
        {
            throw notFound();
        }
        UserMessage message = centerMapper.selectMyMessage(userId, messageId);
        if (message == null)
        {
            throw notFound();
        }
        Map<String, Object> result = toSummary(message);
        result.put("content", message.getContent());
        return result;
    }

    /**
     * 单条已读。幂等：已读时条件更新影响 0 行，但资源确实存在，
     * 因此先做归属存在性判定，再执行幂等更新。
     */
    @Transactional
    public Map<String, Object> markRead(Long userId, Long messageId)
    {
        if (messageId == null)
        {
            throw notFound();
        }
        UserMessage message = centerMapper.selectMyMessage(userId, messageId);
        if (message == null)
        {
            throw notFound();
        }
        Date now = new Date();
        int updated = centerMapper.markRead(userId, messageId, now);
        return Map.of("changed", updated > 0, "readAt", message.getReadAt() == null ? now : message.getReadAt());
    }

    /** 全部已读。幂等：无未读时返回 changed=false。 */
    @Transactional
    public Map<String, Object> markAllRead(Long userId)
    {
        int updated = centerMapper.markAllRead(userId, new Date());
        return Map.of("changed", updated > 0, "updated", updated);
    }

    /**
     * 查询通知偏好：返回完整 渠道×类型 矩阵，未落库的组合按默认开启展开。
     */
    public Map<String, Object> getPreferences(Long userId)
    {
        List<UserNotificationPreference> stored = centerMapper.selectPreferences(userId);
        Map<String, Boolean> storedMap = new LinkedHashMap<>();
        if (stored != null)
        {
            for (UserNotificationPreference pref : stored)
            {
                storedMap.put(pref.getChannel() + "/" + pref.getType(), pref.isEnabled());
            }
        }
        List<Map<String, Object>> matrix = new ArrayList<>();
        for (String channel : CHANNELS)
        {
            for (String type : TYPES)
            {
                Boolean storedValue = storedMap.get(channel + "/" + type);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("channel", channel);
                item.put("type", type);
                item.put("enabled", storedValue == null || storedValue);
                matrix.add(item);
            }
        }
        return Map.of("preferences", matrix);
    }

    /**
     * 保存通知偏好。
     *
     * 传入的组合必须落在支持的 渠道×类型 集合内；未传入的组合保持原值。
     * 等于默认值（开启）的组合删除落库行，保持稀疏存储与「未配置即默认」语义一致。
     */
    @Transactional
    public Map<String, Object> updatePreferences(Long userId, NotificationPreferenceUpdateRequest request)
    {
        if (request == null || request.getPreferences() == null || request.getPreferences().isEmpty())
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "preferences required");
        }
        Set<String> seen = new LinkedHashSet<>();
        int changed = 0;
        for (NotificationPreferenceDto item : request.getPreferences())
        {
            if (item == null)
            {
                throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "preference item invalid");
            }
            String channel = normalizeChannel(item.getChannel());
            String type = normalizeType(item.getType(), false);
            String key = channel + "/" + type;
            if (!seen.add(key))
            {
                throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "duplicate preference item");
            }
            if (item.isEnabled())
            {
                // 默认开启：删除落库行即可表达同一语义
                centerMapper.deletePreference(userId, channel, type);
            }
            else
            {
                centerMapper.upsertPreference(userId, channel, type, false);
            }
            changed++;
        }
        return Map.of("changed", changed > 0);
    }

    static String normalizeChannel(String channel)
    {
        String value = channel == null ? "" : channel.trim().toUpperCase();
        if (!CHANNELS.contains(value))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "invalid channel");
        }
        return value;
    }

    /**
     * 类型校验。
     *
     * @param allowBlank 列表筛选场景允许不传类型（表示全部）
     */
    static String normalizeType(String type, boolean allowBlank)
    {
        if (type == null || type.isBlank())
        {
            if (allowBlank)
            {
                return null;
            }
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "invalid type");
        }
        String value = type.trim().toUpperCase();
        if (!TYPES.contains(value))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "invalid type");
        }
        return value;
    }

    static Map<String, Object> toSummary(UserMessage message)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("messageId", message.getMessageId());
        item.put("type", message.getType());
        item.put("title", message.getTitle());
        item.put("summary", summarize(message.getContent()));
        item.put("read", message.isRead());
        item.put("createdAt", message.getCreatedAt());
        return item;
    }

    private static String summarize(String content)
    {
        if (content == null)
        {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= SUMMARY_LENGTH ? flat : flat.substring(0, SUMMARY_LENGTH) + "…";
    }

    private static AppAuthException notFound()
    {
        return new AppAuthException(AppUserErrorCodes.RESOURCE_NOT_FOUND, 404,
                AppUserErrorCodes.RESOURCE_NOT_FOUND_TEXT);
    }
}
