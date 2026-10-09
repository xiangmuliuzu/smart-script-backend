package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.system.service.NoticeRecipientService;
import com.ruoyi.system.service.NoticeText;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.dto.PageResult;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.UserInboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知和公告统一呈现，仍使用各自原有的收件及阅读记录。 */
@Service
public class UserInboxService
{
    private final UserInboxMapper mapper;
    private final UserMessageService messages;
    private final NoticeRecipientService announcements;

    public UserInboxService(UserInboxMapper mapper, UserMessageService messages, NoticeRecipientService announcements)
    {
        this.mapper = mapper; this.messages = messages; this.announcements = announcements;
    }

    private void requireUser(String userType)
    {
        if (!List.of("01", "02", "03").contains(userType == null ? "" : userType))
            throw new AppAuthException(AppUserErrorCodes.DOMAIN_OR_PERMISSION, 403, "无权读取用户消息");
    }

    public PageResult<Map<String, Object>> page(Long userId, String userType, String type, int pageNum, int pageSize)
    {
        requireUser(userType);
        String filter = UserMessageService.normalizeType(type, true);
        PageHelper.startPage(pageNum, pageSize);
        var rows = mapper.selectInbox(userId, filter);
        long total = new PageInfo<>(rows).getTotal();
        List<Map<String, Object>> items = new ArrayList<>();
        for (var row : rows)
        {
            if ("ANNOUNCEMENT".equals(row.getSource())) row.setContent(NoticeText.plainText(row.getContent()));
            var item = UserMessageService.toSummary(row);
            item.put("source", row.getSource());
            items.add(item);
        }
        return PageResult.of(total, items);
    }

    public Map<String, Object> unreadCount(Long userId, String userType)
    {
        requireUser(userType);
        var original = messages.unreadCount(userId);
        int noticeCount = announcements.unreadCount(userId, userType);
        Map<String, Integer> byType = new LinkedHashMap<>();
        if (original.get("byType") instanceof Map<?, ?> values)
            values.forEach((key, value) -> byType.put(String.valueOf(key), ((Number) value).intValue()));
        byType.merge("SYSTEM", noticeCount, Integer::sum);
        return Map.of("total", ((Number) original.get("total")).intValue() + noticeCount, "byType", byType);
    }

    @Transactional
    public Map<String, Object> markAllRead(Long userId, String userType)
    {
        requireUser(userType);
        int updated = ((Number) messages.markAllRead(userId).get("updated")).intValue()
                + announcements.markAllRead(userId, userType);
        return Map.of("changed", updated > 0, "updated", updated);
    }
}
