package com.smartscript.platform.user.domain.admin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A4 PC 管理域：App 用户详情（契约 §3.1）。
 *
 * 在列表字段之外追加头像、备注与审计摘要。
 * 明确不包含：密码散列、完整手机号、Refresh Session、Token、完整证件号、
 * 实名材料永久地址。
 */
public class AppUserDetail
{
    private Long userId;
    private String userType;
    private String nickname;
    private String phoneMasked;
    private String status;
    private String avatar;
    private String remark;
    private String realNameStatus;
    private Boolean authorCapability;
    private Date createTime;
    private Date lastLoginTime;
    private Date updateTime;

    /** 该用户已授予的角色编码，由批量查询装配，禁止逐行查询。 */
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

    public String getAvatar()
    {
        return avatar;
    }

    public void setAvatar(String avatar)
    {
        this.avatar = avatar;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
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

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
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
