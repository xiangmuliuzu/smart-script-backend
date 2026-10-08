package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * A6 示例业务模块接入统一身份的契约测试（A6-IDENTITY-CONTRACT-v1 §2）。
 *
 * 覆盖 G7 快速门禁的关键点：
 *   - 业务模块通过 IdentityProvider 读取身份，用户 ID 只来自身份而非请求体；
 *   - 私有书架身份块拒绝游客；书架作品数据已由真实书架接口提供（2.7.12）；
 *   - 实名状态用于业务准入，与角色授权独立；
 *   - 身份摘要不下发敏感字段。
 */
class ContentWorkServiceTest
{
    private static IdentityProvider providerOf(IdentityContext identity)
    {
        return () -> identity;
    }

    private static ContentWorkService service(IdentityContext identity)
    {
        return new ContentWorkService(providerOf(identity));
    }

    @Test
    void authenticatedIdentityBlockContainsOwnIdentity()
    {
        IdentityContext identity = IdentityContext.authenticated(
                321L, "02", "0", List.of("author"), List.of("content:work:query"),
                IdentityContext.REAL_NAME_APPROVED, true);
        Map<String, Object> result = service(identity).identityBlock();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("identity");
        assertEquals(Boolean.TRUE, summary.get("authenticated"));
        assertEquals(Boolean.FALSE, summary.get("guest"));
        assertEquals(321L, summary.get("userId"));
        assertEquals("02", summary.get("accountType"));
        assertEquals(IdentityContext.REAL_NAME_APPROVED, summary.get("realNameStatus"));
        assertEquals(Boolean.TRUE, summary.get("authorCapability"));
        assertEquals(List.of("author"), summary.get("roleCodes"));
    }

    @Test
    void identityBlockOmitsSensitiveFields()
    {
        IdentityContext identity = IdentityContext.authenticated(
                321L, "01", "0", List.of(), List.of(), IdentityContext.REAL_NAME_NOT_SUBMITTED, false);
        Map<String, Object> result = service(identity).identityBlock();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("identity");

        // 身份摘要不得下发手机号、昵称、头像或任何凭证
        for (String forbidden : List.of("phoneMasked", "phone", "nickname", "nickName", "avatar",
                "accessToken", "refreshToken", "token", "sessionId", "jti"))
        {
            assertFalse(summary.containsKey(forbidden), "身份摘要不得包含字段: " + forbidden);
        }
    }

    @Test
    void identityBlockUsesUserIdFromIdentityNotFromRequest()
    {
        IdentityContext identity = IdentityContext.authenticated(
                654L, "01", "0", List.of(), List.of(), IdentityContext.REAL_NAME_NOT_SUBMITTED, false);
        Map<String, Object> result = service(identity).identityBlock();

        assertEquals(654L, result.get("ownerUserId"));
        // 书架作品列表由 2.7.12 真实接口提供，身份块不再携带示例作品数据
        assertFalse(result.containsKey("works"));
    }

    @Test
    void identityBlockRealNameGateIsIndependentFromRoles()
    {
        // 有角色但未实名：准入未通过（downloadable=false），且角色不影响该判定
        IdentityContext withRole = IdentityContext.authenticated(
                1L, "01", "0", List.of("author"), List.of("content:work:query"),
                IdentityContext.REAL_NAME_NOT_SUBMITTED, false);
        Map<String, Object> notApproved = service(withRole).identityBlock();
        assertEquals(Boolean.FALSE, notApproved.get("downloadable"));
        assertEquals(Boolean.TRUE, notApproved.get("realNameRequired"));

        // 已实名但无角色：准入通过
        IdentityContext approved = IdentityContext.authenticated(
                2L, "01", "0", List.of(), List.of(), IdentityContext.REAL_NAME_APPROVED, false);
        Map<String, Object> ok = service(approved).identityBlock();
        assertEquals(Boolean.TRUE, ok.get("downloadable"));
        assertEquals(Boolean.FALSE, ok.get("realNameRequired"));

        // 审核中不算通过
        IdentityContext pending = IdentityContext.authenticated(
                3L, "01", "0", List.of(), List.of(), IdentityContext.REAL_NAME_PENDING, false);
        assertEquals(Boolean.FALSE, service(pending).identityBlock().get("downloadable"));
    }

    @Test
    void identityBlockRejectsGuestDefensively()
    {
        // 私有接口正常在过滤器层被拒；服务层仍需防御，避免绕过过滤器时放行
        try
        {
            service(IdentityContext.guest()).identityBlock();
            throw new AssertionError("游客调用书架必须被拒绝");
        }
        catch (IllegalStateException expected)
        {
            assertTrue(expected.getMessage().contains("authenticated"));
        }
    }
}