package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.smartscript.platform.user.domain.ChatMessage;
import com.smartscript.platform.user.domain.ChatSession;
import com.smartscript.platform.user.dto.ChatMessageSendRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.ChatSessionMapper;
import com.smartscript.platform.user.mapper.ChatMessageMapper;

class ChatWorkflowTest
{
    private final ChatSessionMapper sessions = mock(ChatSessionMapper.class);
    private final ChatMessageMapper messages = mock(ChatMessageMapper.class);
    private final ChatService service = new ChatService(sessions, messages);

    private ChatSession session()
    {
        ChatSession session = new ChatSession(); session.setSessionId(17L);
        session.setUser1Id(12L); session.setUser2Id(1L); session.setAssignedAdminId(8L);
        session.setStatus(0); return session;
    }

    @Test
    void openingClaimsTheCurrentAdministratorWithoutChangingBusinessIdentityAndReadsOnlyDisplayedRows()
    {
        ChatSession session = session(); session.setStatus(2);
        when(sessions.selectForUpdate(17L)).thenReturn(session);
        when(sessions.selectById(17L)).thenReturn(session);
        service.openAdminSession(17L, 9L, 23L);
        var ordered = inOrder(sessions, messages);
        ordered.verify(sessions).selectForUpdate(17L);
        ordered.verify(sessions).claimAdmin(17L, 9L);
        ordered.verify(messages).markAdminReadThrough(eq(17L), eq(12L), eq(23L), any());
        ordered.verify(sessions).refreshAdminUnread(17L, 12L);
        verify(sessions, never()).updateAssignedAdmin(any(), any());
        verify(sessions, never()).updateStatus(any(), any());
        assertEquals(1L, session.getUser2Id()); assertEquals(2, session.getStatus());
    }

    @Test
    void refreshingFromAPreviousHandlerCannotReadOrReclaimTheConversation()
    {
        when(sessions.selectForUpdate(17L)).thenReturn(session());
        assertThrows(AppAuthException.class, () -> service.markAdminSessionRead(17L, 1L, 23L));
        verifyNoInteractions(messages);
        verify(sessions, never()).claimAdmin(any(), any());
        verify(sessions, never()).refreshAdminUnread(any(), any());
    }

    @Test
    void readingAnAlreadyDisplayedSnapshotIsIdempotentAndDoesNotReclaim()
    {
        when(sessions.selectForUpdate(17L)).thenReturn(session());
        when(messages.markAdminReadThrough(eq(17L), eq(12L), eq(23L), any())).thenReturn(2, 0);
        assertEquals(Map.of("changed", true), service.markAdminSessionRead(17L, 8L, 23L));
        assertEquals(Map.of("changed", false), service.markAdminSessionRead(17L, 8L, 23L));
        verify(sessions, never()).claimAdmin(any(), any());
    }

    @Test
    void userMessagesAreDeliveredToTheAdministratorWhoOpenedTheConversation()
    {
        when(sessions.selectForUpdate(17L)).thenReturn(session());
        when(messages.selectBySession(17L)).thenReturn(List.of());
        ChatMessageSendRequest request = new ChatMessageSendRequest(); request.setContent("用户后续消息");
        service.sendMessage(17L, 12L, request);
        var message = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages).insertMessage(message.capture());
        assertEquals(8L, message.getValue().getReceiverId());
        verify(sessions).incrementUnread(17L, false);
        assertThrows(AppAuthException.class, () -> service.sendMessage(17L, 1L, request));
    }

    @Test
    void onlyCloseAndReopenAreAvailableAndReopeningUsesTheActiveState()
    {
        when(sessions.selectForUpdate(17L)).thenReturn(session());
        assertEquals(2, service.changeStatus(17L, 8L, "close").get("status"));
        assertEquals(0, service.changeStatus(17L, 8L, "reopen").get("status"));
        assertThrows(AppAuthException.class, () -> service.changeStatus(17L, 8L, "processing"));
        assertThrows(AppAuthException.class, () -> service.changeStatus(17L, 1L, "close"));
        verify(sessions, never()).updateStatus(17L, 1);
    }

    @Test
    void historicalProcessingStatusIsPresentedAsActiveForBothClients()
    {
        ChatSession session = session(); session.setStatus(1);
        when(sessions.selectById(17L)).thenReturn(session);
        when(sessions.selectMySessions(12L, null)).thenReturn(List.of(session));
        assertEquals(0, service.adminSessionDetail(17L).get("status"));
        var user = service.mySessions(12L, null).getList().get(0);
        assertEquals(0, user.get("status")); assertEquals(8L, user.get("peerId"));
    }
}
