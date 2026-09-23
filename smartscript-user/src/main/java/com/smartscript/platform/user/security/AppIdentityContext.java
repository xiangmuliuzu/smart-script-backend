package com.smartscript.platform.user.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * App-domain authentication principal. Not a RuoYi LoginUser.
 */
public class AppIdentityContext extends AbstractAuthenticationToken
{
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String userType;
    private final String userStatus;
    private final String phoneMasked;
    private final String nickName;
    private final String avatar;
    private final String realNameStatus;
    private final Long sessionId;
    private final String jti;
    private final String credentials;

    public AppIdentityContext(Long userId, String userType, String userStatus, String phoneMasked,
            String nickName, String avatar, String realNameStatus, Long sessionId, String jti,
            String credentials)
    {
        super(List.of());
        this.userId = userId;
        this.userType = userType;
        this.userStatus = userStatus;
        this.phoneMasked = phoneMasked;
        this.nickName = nickName;
        this.avatar = avatar;
        this.realNameStatus = realNameStatus;
        this.sessionId = sessionId;
        this.jti = jti;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    public Long getUserId()
    {
        return userId;
    }

    public String getUserType()
    {
        return userType;
    }

    /** 账号状态（若依 status：0 正常 / 1 停用）；供统一身份上下文判定账号可用性。 */
    public String getUserStatus()
    {
        return userStatus;
    }

    public String getPhoneMasked()
    {
        return phoneMasked;
    }

    public String getNickName()
    {
        return nickName;
    }

    public String getAvatar()
    {
        return avatar;
    }

    public String getRealNameStatus()
    {
        return realNameStatus;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public String getJti()
    {
        return jti;
    }

    @Override
    public Object getCredentials()
    {
        return credentials;
    }

    @Override
    public Object getPrincipal()
    {
        return userId;
    }
}
