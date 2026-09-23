package com.smartscript.platform.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一身份上下文（契约 A6-IDENTITY-CONTRACT-v1，规格 §10）。
 *
 * 由 A 模块（{@code smartscript-user}）从服务端认证上下文解析后交给 B/C/D/E 消费；
 * 下游模块只读，不得修改，也**不得**自行查询认证表、Token 表、实名材料表。
 *
 * 放在 {@code ruoyi-common} 而非业务模块的原因：下游只依赖内核即可读取身份，
 * 无需依赖 {@code smartscript-user}，避免业务模块之间出现反向耦合与依赖环。
 *
 * 三条硬约束：
 *   1. {@code userId} 只能来自服务端认证上下文，绝不来自请求体；
 *   2. 游客的 {@code authenticated=false}、{@code guest=true}、{@code userId=null}，
 *      不允许伪造 0 或 -1；
 *   3. 角色（授权）与实名状态（业务准入）分别判断，互不推导。
 *
 * 本类不可变：集合字段一律以不可变视图持有。
 */
public final class IdentityContext
{
    /** 实名状态：未提交（游客与从未提交的账号均为该值）。 */
    public static final String REAL_NAME_NOT_SUBMITTED = "NOT_SUBMITTED";
    /** 实名状态：审核中。 */
    public static final String REAL_NAME_PENDING = "PENDING";
    /** 实名状态：已通过。 */
    public static final String REAL_NAME_APPROVED = "APPROVED";
    /** 实名状态：已驳回。 */
    public static final String REAL_NAME_REJECTED = "REJECTED";

    private static final IdentityContext GUEST = new IdentityContext(
            null, false, null, null, List.of(), List.of(), REAL_NAME_NOT_SUBMITTED, false);

    private final Long userId;
    private final boolean authenticated;
    private final String accountType;
    private final String userStatus;
    private final List<String> roleCodes;
    private final List<String> permissionCodes;
    private final String realNameStatus;
    private final boolean authorCapability;

    private IdentityContext(Long userId, boolean authenticated, String accountType, String userStatus,
            List<String> roleCodes, List<String> permissionCodes, String realNameStatus,
            boolean authorCapability)
    {
        this.userId = userId;
        this.authenticated = authenticated;
        this.accountType = accountType;
        this.userStatus = userStatus;
        this.roleCodes = immutable(roleCodes);
        this.permissionCodes = immutable(permissionCodes);
        this.realNameStatus = realNameStatus == null || realNameStatus.isBlank()
                ? REAL_NAME_NOT_SUBMITTED : realNameStatus;
        this.authorCapability = authorCapability;
    }

    /**
     * 游客身份：未认证、无用户 ID。
     *
     * 公开接口在无 App 身份时使用；不得用「0 号用户」之类写法代替。
     */
    public static IdentityContext guest()
    {
        return GUEST;
    }

    /**
     * 已认证身份。
     *
     * @param userId           服务端认证上下文中的用户 ID，必填
     * @param accountType      账号业务类别（规格 §6.1）
     * @param userStatus       账号状态（若依 status：0 正常 / 1 停用）
     * @param roleCodes        角色编码，用于授权
     * @param permissionCodes  权限标识，用于接口与按钮级授权
     * @param realNameStatus   实名状态，用于业务准入
     * @param authorCapability 是否具备作者能力
     */
    public static IdentityContext authenticated(Long userId, String accountType, String userStatus,
            List<String> roleCodes, List<String> permissionCodes, String realNameStatus,
            boolean authorCapability)
    {
        if (userId == null)
        {
            throw new IllegalArgumentException("authenticated identity requires userId");
        }
        return new IdentityContext(userId, true, accountType, userStatus, roleCodes, permissionCodes,
                realNameStatus, authorCapability);
    }

    public Long getUserId()
    {
        return userId;
    }

    public boolean isAuthenticated()
    {
        return authenticated;
    }

    /** 游客判定；与 {@link #isAuthenticated()} 互斥。 */
    public boolean isGuest()
    {
        return !authenticated;
    }

    public String getAccountType()
    {
        return accountType;
    }

    public String getUserStatus()
    {
        return userStatus;
    }

    public List<String> getRoleCodes()
    {
        return roleCodes;
    }

    public List<String> getPermissionCodes()
    {
        return permissionCodes;
    }

    public String getRealNameStatus()
    {
        return realNameStatus;
    }

    public boolean isAuthorCapability()
    {
        return authorCapability;
    }

    /** 账号是否处于可用状态（未登录时恒为 false）。 */
    public boolean isActive()
    {
        return authenticated && "0".equals(userStatus);
    }

    /** 角色判定：授权用；与实名状态无关。 */
    public boolean hasRole(String roleCode)
    {
        return roleCode != null && roleCodes.contains(roleCode);
    }

    /** 权限判定：支持若依通配 `*:*:*`。 */
    public boolean hasPermission(String permission)
    {
        if (permission == null || permission.isBlank())
        {
            return false;
        }
        return permissionCodes.contains(permission) || permissionCodes.contains("*:*:*");
    }

    /** 实名准入判定：只用于业务准入，不用于授权。 */
    public boolean isRealNameApproved()
    {
        return REAL_NAME_APPROVED.equals(realNameStatus);
    }

    private static List<String> immutable(List<String> source)
    {
        if (source == null || source.isEmpty())
        {
            return List.of();
        }
        List<String> copy = new ArrayList<>(source.size());
        for (String value : source)
        {
            if (value != null && !value.isBlank())
            {
                copy.add(value);
            }
        }
        return copy.isEmpty() ? List.of() : Collections.unmodifiableList(copy);
    }

    /**
     * 不覆盖 toString：避免把身份明细（尤其是角色与权限集合）无意写进日志。
     */
    @Override
    public String toString()
    {
        return "IdentityContext{authenticated=" + authenticated + ", guest=" + isGuest() + "}";
    }
}
