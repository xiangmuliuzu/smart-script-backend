package com.smartscript.platform.user.domain;

import java.util.Date;

public class AppSmsCode
{
    private Long id;
    private String phone;
    private String scene;
    private String codeHash;
    private String requestIp;
    private Integer failedAttempts;
    private Date usedAt;
    private Date expiresAt;
    private Date createTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getScene()
    {
        return scene;
    }

    public void setScene(String scene)
    {
        this.scene = scene;
    }

    public String getCodeHash()
    {
        return codeHash;
    }

    public void setCodeHash(String codeHash)
    {
        this.codeHash = codeHash;
    }

    public String getRequestIp()
    {
        return requestIp;
    }

    public void setRequestIp(String requestIp)
    {
        this.requestIp = requestIp;
    }

    public Integer getFailedAttempts()
    {
        return failedAttempts;
    }

    public void setFailedAttempts(Integer failedAttempts)
    {
        this.failedAttempts = failedAttempts;
    }

    public Date getUsedAt()
    {
        return usedAt;
    }

    public void setUsedAt(Date usedAt)
    {
        this.usedAt = usedAt;
    }

    public Date getExpiresAt()
    {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt)
    {
        this.expiresAt = expiresAt;
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
