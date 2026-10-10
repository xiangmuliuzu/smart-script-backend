package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.ruoyi.system.service.NoticeRecipientService;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.mapper.SysNoticeReadMapper;
import com.ruoyi.system.service.ISysNoticeReadService;

/**
 * 公告已读记录 服务层实现
 *
 * @author ruoyi
 */
@Service
public class SysNoticeReadServiceImpl implements ISysNoticeReadService
{
    @Autowired
    private SysNoticeReadMapper noticeReadMapper;

    @Autowired
    private NoticeRecipientService recipientService;

    /**
     * 标记已读
     */
    @Override
    public void markRead(Long noticeId, Long userId)
    {
        recipientService.markRead(userId, "00", noticeId);
    }

    /**
     * 查询某用户未读公告数量
     */
    @Override
    public int selectUnreadCount(Long userId)
    {
        return recipientService.unreadCount(userId, "00");
    }

    /**
     * 查询公告列表并标记当前用户已读状态
     */
    @Override
    public List<SysNotice> selectNoticeListWithReadStatus(Long userId, int limit)
    {
        PageHelper.startPage(1, Math.max(1, Math.min(100, limit)), false);
        return recipientService.list(userId, "00").stream().map(receipt -> {
            SysNotice notice = new SysNotice();
            notice.setNoticeId(receipt.getNoticeId());
            notice.setNoticeTitle(receipt.getNoticeTitle());
            notice.setNoticeType(receipt.getNoticeType());
            notice.setCreateTime(receipt.getCreateTime());
            notice.setIsRead(receipt.getIsRead());
            return notice;
        }).toList();
    }

    /**
     * 批量标记已读
     */
    @Override
    public void markReadBatch(Long userId, Long[] noticeIds)
    {
        if (noticeIds == null || noticeIds.length == 0)
        {
            return;
        }
        recipientService.markReadBatch(userId, "00", noticeIds);
    }

    /**
     * 删除公告时清理对应已读记录
     */
    @Override
    public void deleteByNoticeIds(Long[] noticeIds)
    {
        noticeReadMapper.deleteByNoticeIds(noticeIds);
    }
}
