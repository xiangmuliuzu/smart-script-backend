package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruoyi.system.service.NoticeRecipientService;
import com.smartscript.platform.user.domain.UserMessage;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.UserInboxMapper;

class UserInboxServiceTest
{
    private final UserInboxMapper mapper = mock(UserInboxMapper.class);
    private final UserMessageService messages = mock(UserMessageService.class);
    private final NoticeRecipientService notices = mock(NoticeRecipientService.class);
    private final UserInboxService service = new UserInboxService(mapper, messages, notices);

    @Test
    void preservesGlobalPageTotalAndDistinctSourcesWithTheSameId()
    {
        Page<UserMessage> rows = new Page<>(1, 2); rows.setTotal(9);
        for (String source : new String[]{"NOTIFICATION", "ANNOUNCEMENT"})
        {
            UserMessage row = new UserMessage(); row.setMessageId(17L); row.setSource(source);
            row.setType("SYSTEM"); row.setTitle("服务升级");
            row.setContent("<p>升级正文</p><script>alert(1)</script>"); rows.add(row);
        }
        when(mapper.selectInbox(12L, "SYSTEM")).thenReturn(rows);
        try
        {
            var result = service.page(12L, "01", "SYSTEM", 1, 2);
            assertEquals(9, result.getTotal()); assertEquals(2, result.getList().size());
            assertEquals("NOTIFICATION", result.getList().get(0).get("source"));
            assertEquals("ANNOUNCEMENT", result.getList().get(1).get("source"));
            assertEquals("升级正文", result.getList().get(1).get("summary"));
        }
        finally { PageHelper.clearPage(); }
    }

    @Test
    void announcementsIncreaseOnlySystemCountWithoutMutatingOriginalCounts()
    {
        var original = Map.of("SYSTEM", 2, "REVIEW", 1);
        when(messages.unreadCount(12L)).thenReturn(Map.of("total", 3, "byType", original));
        when(notices.unreadCount(12L, "03")).thenReturn(4);
        var combined = service.unreadCount(12L, "03");
        assertEquals(7, combined.get("total"));
        assertEquals(Map.of("SYSTEM", 6, "REVIEW", 1), combined.get("byType"));
        assertEquals(2, original.get("SYSTEM"));
    }

    @Test
    void readAllCombinesChangedRowsAndRepeatedCallsAreUnchanged()
    {
        when(messages.markAllRead(12L)).thenReturn(Map.of("updated", 2), Map.of("updated", 0));
        when(notices.markAllRead(12L, "01")).thenReturn(3, 0);
        assertEquals(Map.of("changed", true, "updated", 5), service.markAllRead(12L, "01"));
        assertEquals(Map.of("changed", false, "updated", 0), service.markAllRead(12L, "01"));
    }

    @Test
    void rejectsAdministratorBeforeReadingOrWritingAnOrdinaryUserInbox()
    {
        assertThrows(AppAuthException.class, () -> service.page(1L, "00", null, 1, 10));
        assertThrows(AppAuthException.class, () -> service.unreadCount(1L, "00"));
        assertThrows(AppAuthException.class, () -> service.markAllRead(1L, "00"));
        verifyNoInteractions(mapper, messages, notices);
    }
}
