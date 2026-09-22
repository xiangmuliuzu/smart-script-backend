package com.smartscript.platform.user.domain;

import java.util.Date;

public class AppRefreshSession
{
    private Long id;
    private Long userId;
    private String tokenHash;
    private String familyId;
    private String deviceId;
    private String deviceName;
    private Date expiresAt;
    private Date revokedAt;
    private String revokedReason;
    private Long replacedById;
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

    public String getTokenHash()
    {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash)
    {
        this.tokenHash = tokenHash;
    }

    public String getFamilyId()
    {
        return familyId;
    }

    public void setFamilyId(String familyId)
    {
        this.familyId = familyId;
    }

    public String getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(String deviceId)
    {
        this.deviceId = deviceId;
    }

    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }

    public Date getExpiresAt()
    {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt)
    {
        this.expiresAt = expiresAt;
    }

    public Date getRevokedAt()
    {
        return revokedAt;
    }

    public void setRevokedAt(Date revokedAt)
    {
        this.revokedAt = revokedAt;
    }

    public String getRevokedReason()
    {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason)
    {
        this.revokedReason = revokedReason;
    }

    public Long getReplacedById()
    {
        return replacedById;
    }

    public void setReplacedById(Long replacedById)
    {
        this.replacedById = replacedById;
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
