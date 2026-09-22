package com.smartscript.platform.user.security;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.smartscript.platform.user.service.AppSessionRevocationService;

/**
 * AUTH-20: disabling or deleting an account through the RuoYi PC admin path must
 * also invalidate every App session. Revocation has to happen at the state change
 * and not only when a later request is rejected, because a disabled-then-enabled
 * account would otherwise resurrect its previous Access and Refresh tokens.
 *
 * ruoyi-system must not depend on smartscript-user, so the PC service is observed
 * from the outside with an aspect: all A3 code stays in this module and RuoYi
 * semantics are left untouched. App-side failures are caught so a PC admin
 * operation can never be broken by this integration.
 */
@Aspect
@Component
public class AppAccountStatusRevocationAspect
{
    private static final Logger log = LoggerFactory.getLogger(AppAccountStatusRevocationAspect.class);

    /** sys_user.status: '0' enabled, '1' disabled. */
    private static final String STATUS_ENABLED = "0";

    private final AppSessionRevocationService revocationService;

    public AppAccountStatusRevocationAspect(AppSessionRevocationService revocationService)
    {
        this.revocationService = revocationService;
    }

    /**
     * PC changeStatus endpoint: revoke only when the resulting state is disabled.
     * Matched by package (not by interface name) so the advice still applies if the
     * PC service is proxied through its implementation class rather than its interface.
     */
    @AfterReturning("execution(* com.ruoyi.system.service..*.updateUserStatus(..)) && args(user)")
    public void afterUserStatusChanged(SysUser user)
    {
        if (user != null && user.getUserId() != null && !STATUS_ENABLED.equals(user.getStatus()))
        {
            revoke(user.getUserId(), "ACCOUNT_DISABLED");
        }
    }

    /**
     * PC edit may carry a status change too. RuoYi's update ignores an empty
     * status, so only a non-empty value other than '0' means disabled.
     */
    @AfterReturning("execution(* com.ruoyi.system.service..*.updateUser(..)) && args(user)")
    public void afterUserUpdated(SysUser user)
    {
        if (user == null || user.getUserId() == null || user.getStatus() == null || user.getStatus().isBlank())
        {
            return;
        }
        if (!STATUS_ENABLED.equals(user.getStatus()))
        {
            revoke(user.getUserId(), "ACCOUNT_DISABLED");
        }
    }

    /** PC remove performs a logical delete; every App session for those accounts must end. */
    @AfterReturning("execution(* com.ruoyi.system.service..*.deleteUserByIds(..)) && args(userIds)")
    public void afterUsersDeleted(Long[] userIds)
    {
        if (userIds == null)
        {
            return;
        }
        for (Long userId : userIds)
        {
            revoke(userId, "ACCOUNT_DELETED");
        }
    }

    private void revoke(Long userId, String reason)
    {
        try
        {
            revocationService.revokeAllForUser(userId, reason);
        }
        catch (Exception e)
        {
            log.warn("App session revocation failed for userId={} reason={}: {}", userId, reason, e.getMessage());
        }
    }
}
