package com.smartscript.platform.content;

/**
 * 内容模块标识（与 {@code SmartscriptUserModule} 同约定）。
 *
 * 供装配测试与运维识别模块边界使用；不含业务逻辑。
 */
public final class ContentModule
{
    private ContentModule()
    {
    }

    /** 模块 ID。 */
    public static final String MODULE_ID = "smartscript-content";
}
