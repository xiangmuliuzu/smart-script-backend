package com.ruoyi.web.controller.usercenter;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.github.pagehelper.PageHelper;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.service.AppUserAdminService;

class AdminPaginationCleanupTest
{
    @AfterEach
    void clearTestThread()
    {
        PageHelper.clearPage();
    }

    @Test
    void rejectedFilterDoesNotPaginateNextRequestOnTheSameThread()
    {
        var service = mock(AppUserAdminService.class);
        when(service.page(anyMap())).thenThrow(AppAdminException.badRequest("invalid filter"));
        var controller = new AdminUserCenterController(service, null, null, null, null, null, null, null, null);
        assertThrows(AppAdminException.class, () -> controller.listAppUsers(Map.of(
                "pageNum", "1", "pageSize", "10", "authorCapability", "invalid")));
        assertNull(PageHelper.getLocalPage(), "Rejected filters must not leak pagination across servlet requests");
    }
}
