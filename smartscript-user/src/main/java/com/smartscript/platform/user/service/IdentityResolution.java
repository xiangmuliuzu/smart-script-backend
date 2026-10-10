package com.smartscript.platform.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.user.security.AppIdentityContext;

import java.util.function.Function;

/**
 * 请求级身份解析缓存（契约 A6-IDENTITY-CONTRACT-v1 §1.3）。
 *
 * 为什么需要：一次请求内下游可能多次读取身份（鉴权、审计、业务归属），
 * 角色与权限查询不应重复执行；但**不能**用跨请求缓存或静态字段，
 * 否则会把上一个请求的身份泄漏给下一个请求。
 *
 * 实现方式：Spring 的 request scope。每个 HTTP 请求一个实例，请求结束即回收，
 * 因此天然满足「请求内复用、跨请求隔离」。
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class IdentityResolution
{
    private static final Logger log = LoggerFactory.getLogger(IdentityResolution.class);

    /** 本次请求内已解析的身份；以 userId 为键，避免主体被替换时复用旧值。 */
    private Long resolvedUserId;
    private IdentityContext resolved;

    /**
     * 取本次请求的身份；首次调用执行解析，后续复用。
     *
     * @param principal 当前安全主体
     * @param resolver  解析器（查角色/权限/作者能力）
     */
    public IdentityContext identityFor(AppIdentityContext principal,
            Function<AppIdentityContext, IdentityContext> resolver)
    {
        Long userId = principal.getUserId();
        if (resolved != null && resolvedUserId != null && resolvedUserId.equals(userId))
        {
            return resolved;
        }
        try
        {
            resolved = resolver.apply(principal);
        }
        catch (RuntimeException e)
        {
            // 身份解析失败不得降级为游客（那会静默放宽准入），直接向上抛
            log.warn("identity resolution failed userId={}: {}", userId, e.getClass().getSimpleName());
            throw e;
        }
        resolvedUserId = userId;
        return resolved;
    }
}
