package com.smartscript.platform.user.domain;

import java.util.Date;

/**
 * App 用户域：换绑手机号审计记录（映射 user_phone_change_log，A2 已建表）。
 *
 * 强制边界：只存新旧手机号掩码，不存完整号码、不存验证码（规格 §6.2 / §8.5）。
 */
public class UserPhoneChangeLog
{
    private Long id;
    private Long userId;
    private String oldPhoneMask;
    private String newPhoneMask;
    private String result;
    private String clientIp;
    private String deviceId;
    private Date createTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getOldPhoneMask()
    {
        return oldPhoneMask;
    }

    public void setOldPhoneMask(String oldPhoneMask)
    {
        this.oldPhoneMask = oldPhoneMask;
    }

    public String getNewPhoneMask()
    {
        return newPhoneMask;
    }

    public void setNewPhoneMask(String newPhoneMask)
    {
        this.newPhoneMask = newPhoneMask;
    }

    public String getResult()
    {
        return result;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public String getClientIp()
    {
        return clientIp;
    }

    public void setClientIp(String clientIp)
    {
        this.clientIp = clientIp;
    }

    public String getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(String deviceId)
    {
        this.deviceId = deviceId;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
