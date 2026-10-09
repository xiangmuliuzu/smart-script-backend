package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.NoticeReceipt;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.mapper.NoticeRecipientMapper;
import com.ruoyi.system.mapper.SysNoticeMapper;
import com.ruoyi.system.mapper.SysNoticeReadMapper;
import com.ruoyi.system.service.NoticeRecipientService;
import com.ruoyi.system.service.NoticeText;
import com.ruoyi.system.service.impl.SysNoticeServiceImpl;

class AnnouncementServiceTest
{
    @Test
    void mapsOnlyKnownAccountTypesToAudiences()
    {
        assertEquals("ADMIN", NoticeRecipientService.audienceFor("00"));
        for (String type : List.of("01", "02", "03")) assertEquals("USER", NoticeRecipientService.audienceFor(type));
        assertThrows(ServiceException.class, () -> NoticeRecipientService.audienceFor(null));
        assertThrows(ServiceException.class, () -> NoticeRecipientService.audienceFor("ADMIN"));
    }

    @Test
    void cannotWriteReadForAnInvisibleNotice()
    {
        NoticeRecipientMapper mapper = mock(NoticeRecipientMapper.class);
        NoticeRecipientService service = new NoticeRecipientService(mapper);
        assertEquals(404, assertThrows(ServiceException.class, () -> service.markRead(12L, "01", 8L)).getCode());
        verify(mapper).selectDetail(12L, "USER", 8L);
        verify(mapper, never()).markRead(any(), any(), any());
    }

    @Test
    void repeatedReadDoesNotBecomeAnErrorAndUsesCurrentUser()
    {
        NoticeRecipientMapper mapper = mock(NoticeRecipientMapper.class);
        NoticeReceipt notice = new NoticeReceipt();
        notice.setNoticeId(8L); notice.setIsRead(true); notice.setNoticeContent("正文");
        when(mapper.selectDetail(12L, "USER", 8L)).thenReturn(notice);
        assertFalse(new NoticeRecipientService(mapper).markRead(12L, "02", 8L));
        verify(mapper).markRead(12L, "USER", 8L);
    }

    @Test
    void unreadCountIsIndependentOfListPage()
    {
        NoticeRecipientMapper mapper = mock(NoticeRecipientMapper.class);
        when(mapper.countUnread(12L, "USER")).thenReturn(27);
        assertEquals(27, new NoticeRecipientService(mapper).unreadCount(12L, "03"));
        verify(mapper, never()).selectReceived(any(), any());
    }

    @Test
    void batchValidatesAllIdsBeforeAnyReadWrite()
    {
        NoticeRecipientMapper mapper = mock(NoticeRecipientMapper.class);
        when(mapper.selectDetail(1L, "ADMIN", 2L)).thenReturn(new NoticeReceipt());
        assertThrows(ServiceException.class, () -> new NoticeRecipientService(mapper).markReadBatch(1L, "00", new Long[]{2L, 3L}));
        verify(mapper, never()).markRead(any(), any(), any());
    }

    @Test
    void activeNoticeCannotExpandAudienceEvenWhenTheRequestAlsoClosesIt()
    {
        SysNoticeMapper mapper = mock(SysNoticeMapper.class);
        SysNotice current = notice("ADMIN", "0");
        when(mapper.selectNoticeForUpdate(8L)).thenReturn(current);
        SysNotice update = notice("ALL", "1");
        ServiceException error = assertThrows(ServiceException.class,
                () -> new SysNoticeServiceImpl(mapper, mock(SysNoticeReadMapper.class)).updateNotice(update));
        assertEquals(409, error.getCode());
        verify(mapper, never()).updateNotice(any());
    }

    @Test
    void closedNoticeCanChangeAudienceWithoutClearingReadHistory()
    {
        SysNoticeMapper mapper = mock(SysNoticeMapper.class);
        SysNoticeReadMapper reads = mock(SysNoticeReadMapper.class);
        when(mapper.selectNoticeForUpdate(8L)).thenReturn(notice("ADMIN", "1"));
        SysNotice update = notice("ALL", "0");
        new SysNoticeServiceImpl(mapper, reads).updateNotice(update);
        verify(mapper).updateNotice(update);
        verifyNoInteractions(reads);
    }

    @Test
    void blankOrOversizedContentDoesNotCreateNotice()
    {
        SysNoticeMapper mapper = mock(SysNoticeMapper.class);
        SysNoticeServiceImpl service = new SysNoticeServiceImpl(mapper, mock(SysNoticeReadMapper.class));
        SysNotice invalid = notice("ADMIN", "1"); invalid.setNoticeContent(" \n ");
        assertThrows(ServiceException.class, () -> service.insertNotice(invalid));
        invalid.setNoticeContent("字".repeat(5001));
        assertThrows(ServiceException.class, () -> service.insertNotice(invalid));
        verify(mapper, never()).insertNotice(any());
    }

    @Test
    void deleteLocksSubjectsInOrderBeforeCleaningReads()
    {
        SysNoticeMapper mapper = mock(SysNoticeMapper.class);
        SysNoticeReadMapper reads = mock(SysNoticeReadMapper.class);
        new SysNoticeServiceImpl(mapper, reads).deleteNoticeByIds(new Long[]{8L, 2L, 8L});
        var order = inOrder(mapper, reads);
        order.verify(mapper).selectNoticeForUpdate(2L);
        order.verify(mapper).selectNoticeForUpdate(8L);
        order.verify(reads).deleteByNoticeIds(new Long[]{2L, 8L});
        order.verify(mapper).deleteNoticeByIds(new Long[]{2L, 8L});
    }

    @Test
    void historicalHtmlBecomesReadableTextAndResponseHasNoAdministrativeFields() throws Exception
    {
        assertEquals("标题\n正文 & 内容", NoticeText.plainText("<p>标题</p><p>正文 &amp; 内容</p><script>alert(1)</script>"));
        assertEquals("数值 < 10\n下一行", NoticeText.plainText("数值 < 10\n下一行"));
        NoticeReceipt notice = new NoticeReceipt(); notice.setNoticeId(8L); notice.setIsRead(true);
        String json = new ObjectMapper().writeValueAsString(notice);
        assertTrue(json.contains("\"isRead\":true"));
        assertFalse(json.contains("createBy")); assertFalse(json.contains("remark"));
    }

    @Test
    void missingAndHistoricalTypesAreSavedAsTheUnifiedAnnouncementType()
    {
        SysNoticeMapper mapper = mock(SysNoticeMapper.class);
        SysNoticeServiceImpl service = new SysNoticeServiceImpl(mapper, mock(SysNoticeReadMapper.class));
        SysNotice missing = notice("USER", "1"); missing.setNoticeType(null);
        service.insertNotice(missing); assertEquals("2", missing.getNoticeType());
        SysNotice historical = notice("USER", "1"); historical.setNoticeType("1");
        service.insertNotice(historical); assertEquals("2", historical.getNoticeType());
    }

    private SysNotice notice(String audience, String status)
    {
        SysNotice notice = new SysNotice(); notice.setNoticeId(8L); notice.setNoticeTitle("测试公告");
        notice.setNoticeContent("测试正文"); notice.setNoticeType("2"); notice.setAudience(audience); notice.setStatus(status);
        return notice;
    }
}
