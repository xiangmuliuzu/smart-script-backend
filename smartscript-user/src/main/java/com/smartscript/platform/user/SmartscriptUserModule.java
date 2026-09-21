package com.smartscript.platform.user;

import org.springframework.stereotype.Component;

/**
 * A1 module boundary marker for smartscript-user.
 * Assembled into ruoyi-admin via Maven dependency + component scan of
 * {@code com.smartscript.platform}. Carries no production HTTP API and does not
 * duplicate RuoYi user/role/menu/auth implementations.
 */
@Component
public class SmartscriptUserModule
{
    public static final String MODULE_ID = "smartscript-user";

    public String getModuleId()
    {
        return MODULE_ID;
    }
}
