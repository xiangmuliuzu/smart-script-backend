package com.smartscript.platform.trade;

import org.springframework.stereotype.Component;

/**
 * C module boundary marker for smartscript-trade.
 * Assembled into ruoyi-admin via Maven dependency + component scan of
 * {@code com.smartscript.platform}. Covers trade first-half: partners,
 * demand tags, follow-ups, trade works, inquiries, quotes, orders, demands.
 */
@Component
public class SmartscriptTradeModule
{
    public static final String MODULE_ID = "smartscript-trade";

    public String getModuleId()
    {
        return MODULE_ID;
    }
}
