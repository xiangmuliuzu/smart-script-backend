package com.smartscript.platform.user.service;

import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.system.domain.SysLogininfor;
import com.ruoyi.system.service.ISysLogininforService;

/**
 * Security audit for App authentication events (login success/failure, disabled
 * account login, refresh-token replay, frequency limiting and permission denial).
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
     * 记录一条失败安全事件（登录失败、重放、频控等）。
     *
     * @param actor  masked identity (phone mask or userId); never a credential
     * @param ip     request source, stored like RuoYi stores ipaddr
     * @param event  stable event keyword used by the G3 matrix to assert records
     * @param detail non-sensitive context (scene, limits)
     */
    public void record(String actor, String ip, String event, String detail)
    {
        record(actor, ip, event, detail, false);
    }

    /**
     * 记录一条成功安全事件（登录成功等）。成功行 status='0'，与失败行同表同风格，便于按事件关键字+状态查询。
     */
    public void recordSuccess(String actor, String ip, String event, String detail)
    {
        record(actor, ip, event, detail, true);
    }

    private void record(String actor, String ip, String event, String detail, boolean success)
    {
        String message = detail == null || detail.isBlank() ? event : event + " " + detail;
        // No IP or credential in the application log; the audit row carries the address.
        log.warn("SECURITY_EVENT {} actor={} {}", event, actor, message);
        if (logininforService == null)
        {
            return;
        }
        SysLogininfor row = new SysLogininfor();
        row.setUserName(actor);
        row.setIpaddr(ip);
        row.setStatus(success ? Constants.SUCCESS : Constants.FAIL);
        row.setMsg(truncate(message));
        row.setLoginTime(new Date());
        // H15-REV-03：登录方法在事务内抛出即回滚，审计 INSERT 若同事务会被一并回滚
        // （复现：失败事件行消失）。故行在本线程构建，插入走 AsyncManager 异步队列
        // 在独立连接/事务落库 —— 与若依自身登录审计（AsyncFactory.recordLogininfor）及
        // 第 11 批过滤器链 401 审计（AsyncFactory.recordOper）同模式。
        try
        {
            com.ruoyi.framework.manager.AsyncManager.me().execute(new java.util.TimerTask()
            {
                @Override
                public void run()
                {
                    try
                    {
                        logininforService.insertLogininfor(row);
                    }
                    catch (Exception e)
                    {
                        log.warn("security event audit insert failed event={}: {}", event, e.getMessage());
                    }
                }
            });
        }
        catch (Throwable t)
        {
            // 无 Spring 调度器的环境（纯单测）退回同步落库；生产环境恒走异步（与业务事务解耦）。
            try
            {
                logininforService.insertLogininfor(row);
            }
            catch (Exception e)
            {
                log.warn("security event audit insert failed event={}: {}", event, e.getMessage());
            }
        }
    }

    private static String truncate(String s)
    {
        return s.length() <= 255 ? s : s.substring(0, 255);
    }
}
