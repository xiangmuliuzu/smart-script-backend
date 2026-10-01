package com.smartscript.platform.content.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.identity.IdentityContext;

/**
 * A6 统一身份契约测试（A6-IDENTITY-CONTRACT-v1 §1.2）。
 *
 * 放在 smartscript-content 而不是 ruoyi-common：ruoyi-common 是若依官方边界，
 * 规格 §2.4 要求只做必要扩展，不为测试给官方模块新增依赖；
 * 从消费侧验证身份契约也更贴近下游使用方式。
 *
 * 覆盖 G7 快速门禁关注的语义：游客不伪造用户 ID、角色与实名独立判断、
 * 权限通配、集合不可变、契约字段齐全。
 */
class IdentityContextTest
{
    @Test
    void guestHasNoFabricatedUserId()
    {
        IdentityContext guest = IdentityContext.guest();
        assertFalse(guest.isAuthenticated());
        assertTrue(guest.isGuest());
        // 游客不得用 0/-1 之类占位冒充用户 ID
        assertNull(guest.getUserId());
        assertNull(guest.getAccountType());
        assertNull(guest.getUserStatus());
        assertEquals(IdentityContext.REAL_NAME_NOT_SUBMITTED, guest.getRealNameStatus());
        assertFalse(guest.isAuthorCapability());
        assertFalse(guest.isActive());
        assertTrue(guest.getRoleCodes().isEmpty());
        assertTrue(guest.getPermissionCodes().isEmpty());
    }

    @Test
    void guestIsStableSingletonLikeValue()
    {
        assertFalse(IdentityContext.guest().isAuthenticated());
        assertNull(IdentityContext.guest().getUserId());
    }

    @Test
    void authenticatedRequiresUserId()
    {
        assertThrows(IllegalArgumentException.class, () -> IdentityContext.authenticated(
                null, "01", "0", List.of(), List.of(), IdentityContext.REAL_NAME_NOT_SUBMITTED, false));
    }

    @Test
    void authenticatedExposesAllContractFields()
    {
        IdentityContext identity = IdentityContext.authenticated(
                42L, "02", "0", List.of("author"), List.of("content:work:query", "content:work:add"),
                IdentityContext.REAL_NAME_APPROVED, true);
        assertEquals(42L, identity.getUserId());
        assertTrue(identity.isAuthenticated());
        assertFalse(identity.isGuest());
        assertEquals("02", identity.getAccountType());
        assertEquals("0", identity.getUserStatus());
        assertEquals(List.of("author"), identity.getRoleCodes());
        assertEquals(2, identity.getPermissionCodes().size());
        assertEquals(IdentityContext.REAL_NAME_APPROVED, identity.getRealNameStatus());
        assertTrue(identity.isAuthorCapability());
        assertTrue(identity.isActive());
    }

    @Test
    void roleAndRealNameAreJudgedIndependently()
    {
        // 有角色但未实名：授权通过、业务准入不通过
        IdentityContext withRoleNoRealName = IdentityContext.authenticated(
                1L, "01", "0", List.of("author"), List.of(), IdentityContext.REAL_NAME_NOT_SUBMITTED, false);
        assertTrue(withRoleNoRealName.hasRole("author"));
        assertFalse(withRoleNoRealName.isRealNameApproved());

        // 已实名但无角色：准入通过、没有该角色
        IdentityContext realNameNoRole = IdentityContext.authenticated(
                2L, "01", "0", List.of(), List.of(), IdentityContext.REAL_NAME_APPROVED, false);
        assertTrue(realNameNoRole.isRealNameApproved());
        assertFalse(realNameNoRole.hasRole("author"));

        // 待审核不算通过
        IdentityContext pending = IdentityContext.authenticated(
                3L, "01", "0", List.of(), List.of(), IdentityContext.REAL_NAME_PENDING, false);
        assertFalse(pending.isRealNameApproved());
    }

    @Test
    void permissionSupportsRuoYiWildcard()
    {
        IdentityContext identity = IdentityContext.authenticated(
                7L, "01", "0", List.of(), List.of("*:*:*"), IdentityContext.REAL_NAME_NOT_SUBMITTED, false);
        assertTrue(identity.hasPermission("content:work:add"));
        IdentityContext scoped = IdentityContext.authenticated(
                8L, "01", "0", List.of(), List.of("content:work:query"),
                IdentityContext.REAL_NAME_NOT_SUBMITTED, false);
        assertTrue(scoped.hasPermission("content:work:query"));
        assertFalse(scoped.hasPermission("content:work:add"));
        assertFalse(scoped.hasPermission(null));
        assertFalse(scoped.hasPermission("  "));
    }

    @Test
    void statusAndAuthorCapabilityDefaultsAreConservative()
    {
        // 停用账号：authenticated 为真但 isActive 为假
        IdentityContext disabled = IdentityContext.authenticated(
                9L, "01", "1", List.of(), List.of(), IdentityContext.REAL_NAME_NOT_SUBMITTED, false);
        assertTrue(disabled.isAuthenticated());
        assertFalse(disabled.isActive());

        // 作者能力缺省为 false，不因账号类型推导
        IdentityContext creator = IdentityContext.authenticated(
                10L, "02", "0", List.of(), List.of(), IdentityContext.REAL_NAME_NOT_SUBMITTED, false);
        assertFalse(creator.isAuthorCapability());
    }

    @Test
    void collectionsAreImmutableAndBlankEntriesDropped()
    {
        List<String> roles = new ArrayList<>(List.of("author", "  "));
        IdentityContext identity = IdentityContext.authenticated(
                11L, "01", "0", roles, List.of("content:work:query", ""),
                IdentityContext.REAL_NAME_NOT_SUBMITTED, false);
        assertEquals(List.of("author"), identity.getRoleCodes());
        assertEquals(List.of("content:work:query"), identity.getPermissionCodes());
        // 修改入参集合不得影响已建上下文
        roles.add("admin");
        assertFalse(identity.hasRole("admin"));
        assertThrows(UnsupportedOperationException.class, () -> identity.getRoleCodes().add("x"));
    }

    @Test
    void nullRealNameStatusNormalizesToNotSubmitted()
    {
        IdentityContext identity = IdentityContext.authenticated(
                12L, "01", "0", null, null, null, false);
        assertEquals(IdentityContext.REAL_NAME_NOT_SUBMITTED, identity.getRealNameStatus());
        assertTrue(identity.getRoleCodes().isEmpty());
        assertTrue(identity.getPermissionCodes().isEmpty());
    }

    @Test
    void toStringDoesNotLeakRoleOrPermissionDetails()
    {
        IdentityContext identity = IdentityContext.authenticated(
                13L, "01", "0", List.of("author"), List.of("content:work:add"),
                IdentityContext.REAL_NAME_APPROVED, true);
        String text = identity.toString();
        assertFalse(text.contains("author"), "toString 不得输出角色明细: " + text);
        assertFalse(text.contains("content:work:add"), "toString 不得输出权限明细: " + text);
        assertFalse(text.contains("13"), "toString 不得输出用户 ID: " + text);
    }
}
