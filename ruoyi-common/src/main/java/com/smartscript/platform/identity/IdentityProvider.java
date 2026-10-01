package com.smartscript.platform.identity;

/**
 * 身份上下文提供者（契约 A6-IDENTITY-CONTRACT-v1 §1.5）。
 *
 * B/C/D/E 通过本接口读取当前请求的统一身份摘要，**不直接操作** A 模块的认证表、
 * Token 表、Refresh 会话表或实名材料表。实现由 A 模块提供，运行时由容器装配。
 *
 * 使用约定：
 *   - 公开接口（游客可访问）读取到的是 {@link IdentityContext#guest()}；
 *   - 私有接口由 App 凭证域过滤器先建立身份，因此读取到的一定是已认证身份；
 *   - 不得把返回值缓存到跨请求的静态字段（身份随请求变化）。
 */
public interface IdentityProvider
{
    /**
     * 当前请求的身份上下文；无认证信息时返回游客身份，绝不返回 null。
     */
    IdentityContext currentIdentity();

    /**
     * 当前请求的已认证用户 ID；游客返回 {@code null}。
     *
     * 供只需用户 ID 的场景使用，避免调用方自行判断认证状态。
     */
    default Long currentUserId()
    {
        return currentIdentity().getUserId();
    }
}
