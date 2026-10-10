package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.user.domain.ChatSession;
import com.smartscript.platform.user.mapper.ChatSessionMapper;
import com.smartscript.platform.user.mapper.ChatMessageMapper;

class ChatDeliveryTest
{
    @Test
    void mappingAdminSessionsPreservesTotalAndAdministratorUnread()
    {
        var mapper = mock(ChatSessionMapper.class);
        Page<ChatSession> rows = new Page<>(2, 10);
        rows.setTotal(27);
        var session = new ChatSession();
        session.setSessionId(17L); session.setUser1Unread(0); session.setUser2Unread(3);
        rows.add(session);
        when(mapper.selectAdminSessions(null, null, null)).thenReturn(rows);
        var result = new ChatService(mapper, mock(ChatMessageMapper.class)).adminSessions(null, null, null);
        assertEquals(27, new PageInfo<>(result).getTotal());
        assertEquals(3, result.get(0).get("user2Unread"));
    }

    @Test
    void administratorUnreadUsesAuthenticatedParticipantRatherThanListSize()
    {
        var mapper = mock(ChatSessionMapper.class);
        when(mapper.sumMyUnread(8L)).thenReturn(23);
        assertEquals(Map.of("chatUnread", 23),
                new ChatService(mapper, mock(ChatMessageMapper.class)).chatUnreadCount(8L));
        verify(mapper).sumMyUnread(8L);
    }
}
