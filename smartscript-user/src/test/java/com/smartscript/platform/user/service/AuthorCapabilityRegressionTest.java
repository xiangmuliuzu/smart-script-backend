package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.AppUserAdminMapper;
import com.smartscript.platform.user.mapper.AuthorCapabilityAdminMapper;

class AuthorCapabilityRegressionTest
{
    private final AuthorCapabilityAdminMapper mapper = mock(AuthorCapabilityAdminMapper.class);
    private final AppUserAdminMapper users = mock(AppUserAdminMapper.class);
    private final AuthorCapabilityService service = new AuthorCapabilityService(mapper, users);

    @Test
    void rejectedApplicationCannotBeEnabledDirectly()
    {
        when(users.selectManagedUserId(1L)).thenReturn(1L);
        when(mapper.selectLatestCreatorApplicationStatus(1L)).thenReturn("REJECTED");
        assertThrows(AppAdminException.class, () -> service.setEnabled(1L, true, "开通", 2L, "运营"));
        verify(mapper, never()).upsertCapability(anyLong(), anyBoolean(), anyString(), anyLong(), anyString());
    }

    @Test
    void resubmittedApplicationCanBeEnabledAndExistingCreatorCanBeClosed()
    {
        when(users.selectManagedUserId(1L)).thenReturn(1L);
        when(mapper.selectLatestCreatorApplicationStatus(1L)).thenReturn("PENDING");
        assertTrue(service.setEnabled(1L, true, "重新申请后开通", 2L, "运营"));
        when(mapper.selectEnabledByUserId(1L)).thenReturn(true);
        assertFalse(service.setEnabled(1L, true, "重复开通", 2L, "运营"));
        when(mapper.selectLatestCreatorApplicationStatus(1L)).thenReturn("REJECTED");
        assertTrue(service.setEnabled(1L, false, "关闭", 2L, "运营"));
        verify(mapper).upsertCapability(1L, false, "关闭", 2L, "运营");
    }
}
