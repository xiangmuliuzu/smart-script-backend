package com.smartscript.platform.user.constant;

/**
 * A5 App 用户中心分页常量（契约 §1.1）。
 *
 * 上限与 A4 一致（1<=pageSize<=100），避免同一后端出现两套分页边界。
 */
public final class AppPageConstants
{
    private AppPageConstants()
    {
    }

    /** 默认页码。 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限。 */
    public static final int PAGE_SIZE_MAX = 100;
}
