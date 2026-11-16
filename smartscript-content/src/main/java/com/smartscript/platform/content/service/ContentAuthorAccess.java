package com.smartscript.platform.content.service;

import org.springframework.stereotype.Component;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.identity.IdentityProvider;

/** 创作接口共用准入条件；每次请求读取当前能力，关闭后旧 Token 也不能继续创作。 */
@Component("contentAuthorAccess")
public class ContentAuthorAccess
{
    private final IdentityProvider identityProvider;

    public ContentAuthorAccess(IdentityProvider identityProvider)
    {
        this.identityProvider = identityProvider;
    }

    public boolean allowed()
    {
        IdentityContext identity = identityProvider.currentIdentity();
        return identity.isAuthenticated() && identity.isAuthorCapability();
    }
}
