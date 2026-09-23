package com.smartscript.platform.user.domain.admin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A4 PC 管理域：App 用户列表行（契约 §3.1 AppUserSummary）。
 *
 * 只承载列表所需字段。手机号由 SQL 层直接产出掩码，本对象不持有完整号码。
 * roleCodes 由批量查询装配，禁止逐行查询（SEC-03）。
 */
public class AppUserSummary
{
    private Long userId;
    private String userType;
    private String nickname;
    private String phoneMasked;
    private String status;
    private String realNameStatus;
    private Boolean authorCapability;
    private Date createTime;
    private Date lastLoginTime;

    /** 该用户已授予的角色编码，由批量查询装配。 */
    private List<String> roleCodes = new ArrayList<>();

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserType()
    {
        return userType;
    }

    public void setUserType(String userType)
    {
        this.userType = userType;
    }

    public String getNickname()
    {
        return nickname;
    }

    public void setNickname(String nickname)
    {
        this.nickname = nickname;
    }

    public String getPhoneMasked()
    {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked)
    {
        this.phoneMasked = phoneMasked;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRealNameStatus()
    {
        return realNameStatus;
    }

    public void setRealNameStatus(String realNameStatus)
    {
        this.realNameStatus = realNameStatus;
    }

    public Boolean getAuthorCapability()
    {
        return authorCapability;
    }

    public void setAuthorCapability(Boolean authorCapability)
    {
        this.authorCapability = authorCapability;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getLastLoginTime()
    {
        return lastLoginTime;
    }

    public void setLastLoginTime(Date lastLoginTime)
    {
        this.lastLoginTime = lastLoginTime;
    }

    public List<String> getRoleCodes()
    {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes)
    {
        this.roleCodes = roleCodes == null ? new ArrayList<>() : roleCodes;
    }
}
