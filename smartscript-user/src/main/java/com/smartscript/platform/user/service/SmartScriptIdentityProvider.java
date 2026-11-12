package com.smartscript.platform.user.service;

import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.smartscript.platform.identity.BackgroundIdentity;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.user.mapper.AppIdentityMapper;
import com.smartscript.platform.user.security.AppIdentityContext;

/**
 * A6 统一身份提供者实现（契约 A6-IDENTITY-CONTRACT-v1 §1.3 / §1.5）。
 *
 * 职责：把 App 凭证域已建立的安全主体（{@link AppIdentityContext}）转换为
 * 供 B/C/D/E 消费的 {@link IdentityContext}，并补齐角色、权限、作者能力。
 *
 * 设计要点：
 *   1. **请求内解析一次**：角色/权限/作者能力在首次读取时查库，之后缓存到当前请求
 *      （{@link IdentityResolution} 由 Spring 的 request scope 托管），避免同请求内重复查询；
 *   2. **不伪造身份**：没有 App 主体时返回 {@link IdentityContext#guest()}，
 *      userId 为 null，不用 0/-1 代替；
 *   3. **角色与实名分离**：角色/权限用于授权，实名状态用于业务准入，互不推导；
 *   4. **不泄漏**：不改写 AppIdentityContext（避免把权限集合塞进安全主体并随日志外泄），
 *      IdentityContext.toString 也不输出集合明细。
 */
@Service
public class SmartScriptIdentityProvider implements IdentityProvider
{
    private final AppIdentityMapper identityMapper;
    private final IdentityResolution resolution;

    public SmartScriptIdentityProvider(AppIdentityMapper identityMapper, IdentityResolution resolution)
    {
        this.identityMapper = identityMapper;
        this.resolution = resolution;
    }

    @Override
    public IdentityContext currentIdentity()
    {
        AppIdentityContext app = currentAppIdentity();
        if (app == null)
        {
            // 后台线程（批量生成队列）：没有 HTTP 安全上下文，由任务线程绑定发起人。
            // 队列在自己的作品上执行，作者能力按 true 放行；接口层的归属校验在提交时已完成。
            Long backgroundUserId = BackgroundIdentity.get();
            if (backgroundUserId != null)
            {
                return IdentityContext.authenticated(backgroundUserId, "01", "0",
                        List.of(), List.of(), IdentityContext.REAL_NAME_APPROVED, true);
            }
            // 无 App 身份（公开接口的游客请求，或 PC 凭证）：按游客处理
            return IdentityContext.guest();
        }
        try
        {
            return resolution.identityFor(app, this::resolve);
        }
        catch (RuntimeException e)
        {
            // 请求级缓存（request scope）只在 HTTP 请求线程可用；
            // 后台线程（批量生成队列）已显式绑定了 App 主体，这里退化为直接解析，
            // 否则 every 后台调用都会因「No thread-bound request」失败。
            if (isScopeUnavailable(e))
            {
                return resolve(app);
            }
            throw e;
        }
    }

    /** 判断是否为 request scope 不可用（异步线程），而不是业务解析失败。 */
    private boolean isScopeUnavailable(RuntimeException e)
    {
        if (e instanceof org.springframework.beans.factory.BeanCreationException
                || e instanceof IllegalStateException)
        {
            return true;
        }
        String message = e.getMessage();
        return message != null && (message.contains("request") || message.contains("Scope")
                || message.contains("thread-bound"));
    }

    /**
     * 取当前请求的 App 安全主体。
     *
     * 注意：{@link AppIdentityContext} 本身即 Authentication，而它的
     * {@code getPrincipal()} 返回的是 userId（Long），因此必须判断
     * Authentication 实例本身，不能用 getPrincipal() 做类型判断。
     */
    private AppIdentityContext currentAppIdentity()
    {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof AppIdentityContext app ? app : null;
    }

    /** 角色、权限与作者能力只在首次解析时查库。 */
    private IdentityContext resolve(AppIdentityContext app)
    {
        Long userId = app.getUserId();
        List<String> roleCodes = identityMapper.selectRoleCodes(userId);
        List<String> permissionCodes = identityMapper.selectPermissionCodes(userId);
        return IdentityContext.authenticated(
                userId,
                app.getUserType(),
                app.getUserStatus(),
                roleCodes,
                permissionCodes,
                app.getRealNameStatus(),
                Boolean.TRUE.equals(identityMapper.selectAuthorCapabilityEnabled(userId)));
    }

}
