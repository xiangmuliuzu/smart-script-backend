package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class UserInboxNotifierImplTest
{
    @Test
    void delegatesCreateAndSwallowsFailures()
    {
        UserNotificationAdminService notifications = mock(UserNotificationAdminService.class);
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        org.mockito.Mockito.when(tm.getTransaction(org.mockito.ArgumentMatchers.any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        UserInboxNotifierImpl notifier = new UserInboxNotifierImpl(notifications, tm);

        notifier.notify("SEAL-1", "SEAL", "标题", "正文", "SEAL", "9", List.of(1L));
        verify(notifications).create(eq("SEAL-1"), eq("SEAL"), eq("标题"), eq("正文"),
                eq("SEAL"), eq("9"), eq(List.of(1L)), eq("system"));

        doThrow(new RuntimeException("boom")).when(notifications)
                .create(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyList(), anyString());
        assertDoesNotThrow(() -> notifier.notify("SEAL-2", "SEAL", "标题", "正文", "SEAL", "9", List.of(1L)));
    }
}
