package com.smartscript.platform.user.constant;

/**
 * A4 PC 管理接口错误语义（契约 §6）。
 *
 * 与若依 AjaxResult.code 一致：HTTP 状态与响应体 code 同值。
 * 错误文案不得暴露 SQL、内部类名、堆栈、文件路径、材料地址
 * 或目标账号是否存在的敏感细节。
 */
public final class AppAdminErrorCodes
{
    private AppAdminErrorCodes()
    {
    }

    /** 200 成功。 */
    public static final int OK = 200;

    /** 400 参数、分页、排序或状态动作无效。 */
    public static final int BAD_REQUEST = 400;

    /** 401 PC 未登录或 Token 无效。 */
    public static final int UNAUTHORIZED = 401;

    /** 403 权限不足或凭证域错误（App Token 访问 PC 管理接口亦归此类）。 */
    public static final int FORBIDDEN = 403;

    /** 404 资源不存在或不属于 A4 可管理账号域。 */
    public static final int NOT_FOUND = 404;

    /** 409 状态已变化、并发决定、幂等键冲突或角色边界冲突。 */
    public static final int CONFLICT = 409;

    /** 429 操作频率超过安全限制。 */
    public static final int TOO_MANY_REQUESTS = 429;

    /** 500 系统错误。 */
    public static final int SYSTEM_ERROR = 500;

    /**
     * 账号不属于 A4 可管理域的对外文案。
     * 刻意与「不存在」同文案，避免泄露目标账号是否存在的敏感细节。
     */
    public static final String RESOURCE_NOT_FOUND = "资源不存在";

    /** 409 通用文案。 */
    public static final String STATE_CONFLICT = "状态已变化，请刷新后重试";

    /** 409 幂等键冲突文案。 */
    public static final String IDEMPOTENCY_CONFLICT = "相同请求编号已被不同内容使用";

    /** 409 角色边界冲突文案。 */
    public static final String ROLE_BOUNDARY_CONFLICT = "存在不可授予 App 用户的角色";

    /** 400 参数文案。 */
    public static final String INVALID_PARAM = "请求参数无效";

    /** 500 系统错误文案（不含内部细节）。 */
    public static final String SYSTEM_ERROR_TEXT = "系统错误，请联系管理员";
}
