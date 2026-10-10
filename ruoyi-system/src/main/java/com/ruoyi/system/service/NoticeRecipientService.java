package com.ruoyi.system.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.NoticeReceipt;
import com.ruoyi.system.mapper.NoticeRecipientMapper;

/** 后台和 App 共用公告接收规则；参数来自认证上下文，不能来自请求体。 */
@Service
public class NoticeRecipientService
{
    private final NoticeRecipientMapper mapper;

    public NoticeRecipientService(NoticeRecipientMapper mapper) { this.mapper = mapper; }

    public static String audienceFor(String userType)
    {
        if ("00".equals(userType)) return "ADMIN";
        if ("01".equals(userType) || "02".equals(userType) || "03".equals(userType)) return "USER";
        throw new ServiceException("账号类型无权阅读公告", 403);
    }

    public List<NoticeReceipt> list(Long userId, String userType)
    {
        return mapper.selectReceived(userId, audienceFor(userType));
    }

    public NoticeReceipt detail(Long userId, String userType, Long noticeId)
    {
        NoticeReceipt notice = mapper.selectDetail(userId, audienceFor(userType), noticeId);
        if (notice == null) throw new ServiceException("公告不存在或已关闭", 404);
        notice.setNoticeContent(NoticeText.plainText(notice.getNoticeContent()));
        return notice;
    }

    public int unreadCount(Long userId, String userType)
    {
        return mapper.countUnread(userId, audienceFor(userType));
    }

    @Transactional
    public boolean markRead(Long userId, String userType, Long noticeId)
    {
        detail(userId, userType, noticeId);
        int updated = mapper.markRead(userId, audienceFor(userType), noticeId);
        // 写入 SQL 再次检查范围与状态；并发关闭/删除不能视作已读成功。
        if (updated == 0) detail(userId, userType, noticeId);
        return updated > 0;
    }

    @Transactional
    public int markAllRead(Long userId, String userType)
    {
        return mapper.markAllRead(userId, audienceFor(userType));
    }

    @Transactional
    public void markReadBatch(Long userId, String userType, Long[] ids)
    {
        if (ids == null || ids.length > 100) throw new ServiceException("单次最多处理100条公告", 400);
        for (Long id : ids) detail(userId, userType, id);
        for (Long id : ids) markRead(userId, userType, id);
    }
}
