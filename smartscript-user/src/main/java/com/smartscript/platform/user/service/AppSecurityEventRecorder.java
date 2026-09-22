package com.smartscript.platform.user.service;

import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.system.domain.SysLogininfor;
import com.ruoyi.system.service.ISysLogininforService;

/**
 * Security audit for App authentication events (frequency limiting, session
 * replay, account disable/delete and permission denial).
 *
 * Reuses the existing RuoYi audit table (sys_logininfor) instead of adding an A3
 * table: A3 must not change the shared database schema. Actors are recorded
 * masked and no token, password or verification code ever reaches this sink.
 *
 * Recording must never break the security decision it documents, so failures are
 * logged and swallowed.
 */
@Service
public class AppSecurityEventRecorder
{
    private static final Logger log = LoggerFactory.getLogger(AppSecurityEventRecorder.class);

    private final ISysLogininforService logininforService;

    public AppSecurityEventRecorder(ISysLogininforService logininforService)
    {
        this.logininforService = logininforService;
    }

    /**
     * @param actor  masked identity (phone mask or userId); never a credential
     * @param ip     request source, stored like RuoYi stores ipaddr
     * @param event  stable event keyword used by the G3 matrix to assert records
     * @param detail non-sensitive context (scene, limits)
     */
    public void record(String actor, String ip, String event, String detail)
    {
        String message = detail == null || detail.isBlank() ? event : event + " " + detail;
        try
        {
            if (logininforService != null)
            {
                SysLogininfor row = new SysLogininfor();
                row.setUserName(actor);
                row.setIpaddr(ip);
                row.setStatus(Constants.FAIL);
                row.setMsg(truncate(message));
                row.setLoginTime(new Date());
                logininforService.insertLogininfor(row);
            }
        }
        catch (Exception e)
        {
            log.warn("security event audit failed event={} actor={}: {}", event, actor, e.getMessage());
        }
        // No IP or credential in the application log; the audit row carries the address.
        log.warn("SECURITY_EVENT {} actor={} {}", event, actor, message);
    }

    private static String truncate(String s)
    {
        return s.length() <= 255 ? s : s.substring(0, 255);
    }
}
