package com.smartscript.platform.user.domain;

import java.util.Date;

public class AppUserConsent
{
    private Long id;
    private Long userId;
    private String agreementType;
    private String agreementVersion;
    private Date acceptedAt;
    private String ip;
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

    public String getAgreementType()
    {
        return agreementType;
    }

    public void setAgreementType(String agreementType)
    {
        this.agreementType = agreementType;
    }

    public String getAgreementVersion()
    {
        return agreementVersion;
    }

    public void setAgreementVersion(String agreementVersion)
    {
        this.agreementVersion = agreementVersion;
    }

    public Date getAcceptedAt()
    {
        return acceptedAt;
    }

    public void setAcceptedAt(Date acceptedAt)
    {
        this.acceptedAt = acceptedAt;
    }

    public String getIp()
    {
        return ip;
    }

    public void setIp(String ip)
    {
        this.ip = ip;
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
