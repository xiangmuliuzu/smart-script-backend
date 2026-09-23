package com.smartscript.platform.user.constant;

/**
 * A5 App 用户中心错误语义（契约 A5-USER-CENTER-CONTRACT-v1 §1.7）。
 *
 * 取值与 A3（AppAuthErrorCodes）保持同一编码语言：4xx 表示客户端可纠正的问题，
 * 5xx 表示服务端问题。新增码不得与 A3 已有码冲突。
 *
 * 文案不得暴露 SQL、内部类名、堆栈、文件路径、材料地址或他人账号是否存在。
 */
public final class AppUserErrorCodes
{
    private AppUserErrorCodes()
    {
    }

    /** 400 参数、长度或格式无效（沿用 A3 的同值码）。 */
    public static final int PARAM = 40000;

    /** 401 未登录或凭证无效（沿用 A3 的同值码）。 */
    public static final int UNAUTHORIZED = 40100;

    /** 403 凭证域错误或权限不足（沿用 A3 的同值码）。 */
    public static final int DOMAIN_OR_PERMISSION = 40300;

    /** 403 账号停用或删除（沿用 A3 的同值码）。 */
    public static final int ACCOUNT_DISABLED = 40301;

    /** 404 资源不存在，或不属于当前用户（他人资源一律同文案）。 */
    public static final int RESOURCE_NOT_FOUND = 40400;

    /** 409 手机号已被占用（沿用 A3 的同值码）。 */
    public static final int PHONE_TAKEN = 40902;

    /** 409 实名状态冲突：审核中或已通过时重复提交。 */
    public static final int REAL_NAME_CONFLICT = 40905;

    /** 409 重复提交。 */
    public static final int DUPLICATE_SUBMIT = 40906;

    /** 404 通用文案：与「不属于我」同文案，避免泄露资源是否存在。 */
    public static final String RESOURCE_NOT_FOUND_TEXT = "资源不存在";

    /** 409 实名状态冲突文案。 */
    public static final String REAL_NAME_CONFLICT_TEXT = "当前实名状态不允许该操作";

    /** 409 重复提交文案。 */
    public static final String DUPLICATE_SUBMIT_TEXT = "请勿重复提交";

    /** 400 通用文案。 */
    public static final String INVALID_PARAM = "请求参数无效";
}
