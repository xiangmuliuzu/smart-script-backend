package com.ruoyi.system.service.impl;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.mapper.SysNoticeMapper;
import com.ruoyi.system.mapper.SysNoticeReadMapper;
import com.ruoyi.system.service.ISysNoticeService;
import com.ruoyi.system.service.NoticeText;

/** 公告管理：校验内容、维护接收范围、事务性删除主体和已读记录。 */
@Service
public class SysNoticeServiceImpl implements ISysNoticeService
{
    private final SysNoticeMapper mapper;
    private final SysNoticeReadMapper readMapper;

    public SysNoticeServiceImpl(SysNoticeMapper mapper, SysNoticeReadMapper readMapper)
    {
        this.mapper = mapper;
        this.readMapper = readMapper;
    }

    @Override
    public SysNotice selectNoticeById(Long noticeId)
    {
        SysNotice notice = mapper.selectNoticeById(noticeId);
        if (notice == null) throw new ServiceException("公告不存在", 404);
        notice.setNoticeContent(NoticeText.plainText(notice.getNoticeContent()));
        return notice;
    }

    @Override
    public List<SysNotice> selectNoticeList(SysNotice notice)
    {
        return mapper.selectNoticeList(notice);
    }

    @Override
    public int insertNotice(SysNotice notice)
    {
        if (notice.getStatus() == null) notice.setStatus("1");
        if (notice.getAudience() == null) notice.setAudience("ADMIN");
        validate(notice);
        return mapper.insertNotice(notice);
    }

    @Override
    @Transactional
    public int updateNotice(SysNotice notice)
    {
        SysNotice current = mapper.selectNoticeForUpdate(notice.getNoticeId());
        if (current == null) throw new ServiceException("公告不存在", 404);
        // 兼容旧管理请求缺省范围，保留已有范围，不把它重置成 ADMIN。
        if (notice.getAudience() == null) notice.setAudience(current.getAudience());
        if (notice.getStatus() == null) notice.setStatus(current.getStatus());
        validate(notice);
        if ("0".equals(current.getStatus()) && !current.getAudience().equals(notice.getAudience()))
            throw new ServiceException("请先关闭公告，再修改接收范围", 409);
        return mapper.updateNotice(notice);
    }

    @Override
    @Transactional
    public int deleteNoticeById(Long noticeId)
    {
        return deleteNoticeByIds(new Long[] {noticeId});
    }

    @Override
    @Transactional
    public int deleteNoticeByIds(Long[] noticeIds)
    {
        if (noticeIds == null || noticeIds.length == 0 || noticeIds.length > 100
                || Arrays.stream(noticeIds).anyMatch(id -> id == null || id <= 0))
            throw new ServiceException("公告编号无效，单次最多删除100条", 400);
        Long[] ids = Arrays.stream(noticeIds).distinct().sorted().toArray(Long[]::new);
        // 先锁定主体，再清理阅读记录；与按公告主体读取的已读写入串行化。
        for (Long id : ids) mapper.selectNoticeForUpdate(id);
        readMapper.deleteByNoticeIds(ids);
        return mapper.deleteNoticeByIds(ids);
    }

    private void validate(SysNotice notice)
    {
        String title = notice.getNoticeTitle() == null ? "" : notice.getNoticeTitle().strip();
        String content = NoticeText.plainText(notice.getNoticeContent());
        if (title.isBlank() || title.length() > 50) throw new ServiceException("公告标题必填且不超过50个字符", 400);
        if (content.isBlank() || content.length() > 5000) throw new ServiceException("公告正文必填且不超过5000个字符", 400);
        if (notice.getNoticeType() != null && !List.of("1", "2").contains(notice.getNoticeType()))
            throw new ServiceException("公告类型无效", 400);
        // 通知与公告统一管理；历史类型1兼容读取，所有新写入统一为公告。
        notice.setNoticeType("2");
        if (!List.of("0", "1").contains(notice.getStatus())) throw new ServiceException("公告状态无效", 400);
        if (!List.of("USER", "ADMIN", "ALL").contains(notice.getAudience())) throw new ServiceException("公告接收范围无效", 400);
        notice.setNoticeTitle(title);
        notice.setNoticeContent(content);
    }
}
