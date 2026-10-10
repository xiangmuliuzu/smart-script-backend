package com.smartscript.platform.user.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.service.AppSessionRevocationService;

/**
 * AUTH-20 regression: a PC-side disable/delete must revoke the App sessions for
 * that account. Previously the PC paths never called the revocation service, so a
 * disabled-then-enabled account resurrected its old Access and Refresh tokens.
 */
class AppAccountStatusRevocationAspectTest
{
    /** Records which userIds were revoked and why. */
    static class CapturingRevocation extends AppSessionRevocationService
    {
        final List<String> revoked = new ArrayList<>();

        CapturingRevocation()
        {
            super(new AppAuthProperties(), null, null);
        }

        @Override
        public void revokeAllForUser(Long userId, String reason)
        {
            revoked.add(userId + ":" + reason);
        }
    }

    private static SysUser user(long id, String status)
    {
        SysUser u = new SysUser();
        u.setUserId(id);
        u.setStatus(status);
        return u;
    }

    @Test
    void disablingUserRevokesAllAppSessions()
    {
        CapturingRevocation revocation = new CapturingRevocation();
        AppAccountStatusRevocationAspect aspect = new AppAccountStatusRevocationAspect(revocation);

        aspect.afterUserStatusChanged(user(42L, "1"));

        assertEquals(List.of("42:ACCOUNT_DISABLED"), revocation.revoked);
    }

    @Test
    void enablingUserDoesNotRevoke()
    {
        CapturingRevocation revocation = new CapturingRevocation();
        AppAccountStatusRevocationAspect aspect = new AppAccountStatusRevocationAspect(revocation);

        aspect.afterUserStatusChanged(user(42L, "0"));

        assertTrue(revocation.revoked.isEmpty(), "enabling must not revoke, got " + revocation.revoked);
    }

    @Test
    void editWithDisabledStatusRevokes()
    {
        CapturingRevocation revocation = new CapturingRevocation();
        AppAccountStatusRevocationAspect aspect = new AppAccountStatusRevocationAspect(revocation);

        aspect.afterUserUpdated(user(42L, "1"));

        assertEquals(List.of("42:ACCOUNT_DISABLED"), revocation.revoked);
    }

    @Test
    void editWithoutStatusChangeDoesNotRevoke()
    {
        CapturingRevocation revocation = new CapturingRevocation();
        AppAccountStatusRevocationAspect aspect = new AppAccountStatusRevocationAspect(revocation);

        aspect.afterUserUpdated(user(42L, ""));

        assertTrue(revocation.revoked.isEmpty(), "blank status must not revoke, got " + revocation.revoked);
    }

    @Test
    void deletingUsersRevokesEachAccount()
    {
        CapturingRevocation revocation = new CapturingRevocation();
        AppAccountStatusRevocationAspect aspect = new AppAccountStatusRevocationAspect(revocation);

        aspect.afterUsersDeleted(new Long[] { 7L, 8L });

        assertEquals(List.of("7:ACCOUNT_DELETED", "8:ACCOUNT_DELETED"), revocation.revoked);
    }
}
