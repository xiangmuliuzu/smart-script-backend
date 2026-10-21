package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.service.impl.AppWorkManageServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.identity.WorkDeletionVerification;

class AppWorkDeletionTest
{
    private final SysContentWorkMapper works = mock(SysContentWorkMapper.class);
    private final SysTagMapper tags = mock(SysTagMapper.class);
    private final WorkDeletionVerification verification = mock(WorkDeletionVerification.class);
    private final IdentityProvider identity = mock(IdentityProvider.class);
    private final AppWorkManageServiceImpl service = new AppWorkManageServiceImpl();

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(service, "workMapper", works);
        ReflectionTestUtils.setField(service, "tagMapper", tags);
        ReflectionTestUtils.setField(service, "deletionVerification", verification);
        ReflectionTestUtils.setField(service, "identityProvider", identity);
        when(identity.currentUserId()).thenReturn(12L);
    }

    private void owned()
    {
        when(works.selectOwnedWork(7L, 12L)).thenReturn(new SysWork());
    }

    @Test
    void missingOrForeignWorkCannotSendOrConsumeCodes()
    {
        assertNull(service.sendDeletionCode(7L, "127.0.0.1", null));
        assertFalse(service.deleteWork(7L, "123456"));
        verifyNoInteractions(verification, tags);
        verify(works, never()).softDeleteWork(any());
    }

    @Test
    void controllerRejectsOldClientsWithoutVerificationBody()
    {
        owned();
        var controller = new com.smartscript.platform.content.controller.app.AppWorkManageController(service, null);
        assertEquals(40000, controller.delete(7L, null).getCode());
        assertEquals(40000, controller.delete(7L, Map.of("userId", 999L)).getCode());
        verify(works, never()).softDeleteWork(any());
    }

    @Test
    void missingAndMalformedCodesCannotDelete()
    {
        owned();
        for (String code : new String[] {null, "", "123", "abcdef", "123456789"})
        {
            assertEquals(40000, assertThrows(ServiceException.class,
                    () -> service.deleteWork(7L, code)).getCode());
        }
        verifyNoInteractions(verification, tags);
        verify(works, never()).softDeleteWork(any());
    }

    @Test
    void transactionHistoryBlocksSendingAndDeletion()
    {
        owned();
        when(works.hasWorkTransactions(7L)).thenReturn(true);
        assertEquals(409, assertThrows(ServiceException.class,
                () -> service.sendDeletionCode(7L, "127.0.0.1", null)).getCode());
        assertEquals(409, assertThrows(ServiceException.class,
                () -> service.deleteWork(7L, "123456")).getCode());
        verifyNoInteractions(verification, tags);
        verify(works, never()).softDeleteWork(any());
    }

    @Test
    void invalidExpiredAndReusedCodesNeverReachDeletion()
    {
        owned();
        for (int code : new int[] {40001, 40002, 40901})
        {
            doThrow(new ServiceException("sms rejected", code))
                    .when(verification).consumeCode(12L, "123456");
            assertEquals(code, assertThrows(ServiceException.class,
                    () -> service.deleteWork(7L, "123456")).getCode());
        }
        verifyNoInteractions(tags);
        verify(works, never()).softDeleteWork(any());
    }

    @Test
    void sendsCodeForAuthenticatedOwnerOnly()
    {
        owned();
        when(verification.sendCode(12L, "127.0.0.1", "request"))
                .thenReturn(Map.of("sent", true, "phoneMasked", "139****0001"));
        assertEquals("139****0001", service.sendDeletionCode(7L, "127.0.0.1", "request")
                .get("phoneMasked"));
    }

    @Test
    void successfulVerificationPrecedesOwnedSoftDeletionAndTagRefresh()
    {
        owned();
        when(works.softDeleteWork(any())).thenReturn(1);
        assertTrue(service.deleteWork(7L, "123456"));
        var order = inOrder(verification, works, tags);
        order.verify(verification).consumeCode(12L, "123456");
        order.verify(works).softDeleteWork(argThat(w -> w.getWorkId().equals(7L)
                && w.getAuthorId().equals(12L) && "12".equals(w.getUpdateBy())));
        order.verify(tags).syncTagUseCount();
    }

    @Test
    void changedWorkThrowsSoTransactionCanRollBackConsumedCode()
    {
        owned();
        when(works.softDeleteWork(any())).thenReturn(0);
        assertEquals(409, assertThrows(ServiceException.class,
                () -> service.deleteWork(7L, "123456")).getCode());
        verifyNoInteractions(tags);
    }
}
