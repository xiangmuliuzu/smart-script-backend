package com.ruoyi.framework.security.handle;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 过滤器链层认证/授权拒绝（401 与 App 过滤器 403）审计回调。
 *
 * 过滤器链层的 401 与 App 过滤器产生的 403 发生在 {@code DispatcherServlet} 之前
 * （PC 401 走 {@link AuthenticationEntryPointImpl}，App 401/403 走 App 凭证域过滤器），
 * MVC 层的操作日志切面与异常处理器都拿不到，因此需要在安全判定点预留一个回调钩子。
 * 框架层只定义 SPI、只负责「在响应写完后回调」，不含任何业务判断；
 * 是否记录、记录成什么口径（路径白名单、标题、安全文案、URL 脱敏）由实现方决定。
 *
 * 实现方必须自行吞掉所有异常：审计可以少记，但不能让被拒请求的响应失败。
 * 实现方还必须保证同一请求只记录一次（请求级一次性标记）。
 *
 * @author smartscript
 */
public interface AuthenticationFailureAuditor
{
    /** PC（若依后台凭证域）。 */
    String DOMAIN_PC = "PC";

    /** App 凭证域。 */
    String DOMAIN_APP = "APP";

    /** App 403 事件类型：凭证域/权限不符（AppAuthErrorCodes.DOMAIN_OR_PERMISSION）。 */
    String REASON_DOMAIN_OR_PERMISSION = "DOMAIN_OR_PERMISSION";

    /** App 403 事件类型：账号禁用（AppAuthErrorCodes.ACCOUNT_DISABLED）。 */
    String REASON_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

    /**
     * 记录一次过滤器链层认证拒绝（401）。
     *
     * @param request    当前请求（实现方只读，不得写入响应）
     * @param domain     {@link #DOMAIN_PC} 或 {@link #DOMAIN_APP}
     * @param httpStatus 该安全决定对应的语义状态码（401）
     */
    void record(HttpServletRequest request, String domain, int httpStatus);

    /**
     * 记录一次过滤器链层拒绝（401 或 App 过滤器 403），实现方可按 {@code reason} 区分事件类型。
     * 缺省实现退回三参回调，保持既有实现兼容。
     *
     * @param request    当前请求（实现方只读，不得写入响应）
     * @param domain     {@link #DOMAIN_PC} 或 {@link #DOMAIN_APP}
     * @param httpStatus 该安全决定对应的语义状态码（401 或 403）
     * @param reason     稳定的事件类型标记（如 {@link #REASON_DOMAIN_OR_PERMISSION}）；可为 null
     */
    default void record(HttpServletRequest request, String domain, int httpStatus, String reason)
    {
        record(request, domain, httpStatus);
    }
}
