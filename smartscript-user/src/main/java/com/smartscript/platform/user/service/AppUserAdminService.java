package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.constant.AppAdminErrorCodes;
import com.smartscript.platform.user.domain.admin.AppUserCapabilityRow;
import com.smartscript.platform.user.domain.admin.AppUserDetail;
import com.smartscript.platform.user.domain.admin.AppUserRealNameRow;
import com.smartscript.platform.user.domain.admin.AppUserRoleRow;
import com.smartscript.platform.user.domain.admin.AppUserSummary;
import com.smartscript.platform.user.domain.admin.GrantableRole;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.AppUserAdminMapper;

/**
 * A4 App 用户管理（契约 §5.1）。
 *
 * 职责：App 用户分页与筛选、详情装配、状态变更、角色授权及会话吊销。
 *
 * 强制边界：
 *   - 账号域由 Mapper SQL 限定为 01/02/03；本服务再做防御性校验，
 *     PC 管理员（user_type=00）一律按「不存在」处理，不区分文案。
 *   - 列表装配角色/实名/作者状态使用 IN 批量查询，禁止 N+1（SEC-03）。
 *   - 排序字段走白名单，禁止把客户端字段名直接拼接 SQL（SEC-01）。
 *   - 状态与角色变更在事务内完成，成功后吊销该用户全部 App 会话（USER-06/08）。
 */
@Service
public class AppUserAdminService
{
    private final AppUserAdminMapper mapper;
    private final AppSessionRevocationService revocationService;

    /**
     * 排序字段白名单：客户端只能传这些逻辑名，映射到固定的物理列。
     * 未命中白名单时回落到默认排序，不把原始输入拼进 SQL。
     */
    private static final Map<String, String> ORDER_WHITELIST;

    static
    {
        Map<String, String> m = new HashMap<>();
        m.put("userId", "u.user_id");
        m.put("createTime", "u.create_time");
        m.put("lastLoginTime", "u.login_date");
        m.put("status", "u.status");
        m.put("userType", "u.user_type");
        ORDER_WHITELIST = Collections.unmodifiableMap(m);
    }

    public AppUserAdminService(AppUserAdminMapper mapper, AppSessionRevocationService revocationService)
    {
        this.mapper = mapper;
        this.revocationService = revocationService;
    }

    /**
     * 分页查询。PageHelper 由 Controller 层驱动，本方法只负责参数净化与装配。
     */
    public List<AppUserSummary> page(Map<String, Object> params)
    {
        Map<String, Object> safe = sanitize(params);
        List<AppUserSummary> rows = mapper.selectAppUserPage(safe);
        if (rows == null || rows.isEmpty())
        {
            return new ArrayList<>();
        }
        List<Long> ids = rows.stream().map(AppUserSummary::getUserId).toList();
        Map<Long, List<String>> roleMap = loadRoleCodes(ids);
        for (AppUserSummary row : rows)
        {
            row.setRoleCodes(roleMap.getOrDefault(row.getUserId(), Collections.emptyList()));
        }
        return rows;
    }

    public AppUserDetail detail(Long userId)
    {
        requireManaged(userId);
        AppUserDetail detail = mapper.selectAppUserDetail(userId);
        if (detail == null)
        {
            throw AppAdminException.notFound();
        }
        Map<Long, List<String>> roleMap = loadRoleCodes(List.of(userId));
        detail.setRoleCodes(roleMap.getOrDefault(userId, Collections.emptyList()));
        return detail;
    }

    /**
     * 可授予 App 用户的角色清单（契约 §5.1）。
     * 权威来源为 sys_role.app_grantable，SQL 层已排除停用、删除与超级管理员。
     */
    public List<GrantableRole> grantableRoles()
    {
        List<GrantableRole> roles = mapper.selectGrantableRoles();
        return roles == null ? new ArrayList<>() : roles;
    }

    /**
     * 变更 App 用户状态（契约 §5.1）。
     *
     * 重复设置同一状态幂等（USER-07）：不重复吊销、不产生错误审计。
     * 禁用成功后在同一业务操作中吊销全部 App 会话（USER-06）。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long userId, String targetStatus, String reason, String operator)
    {
        if (reason == null || reason.isBlank())
        {
            throw AppAdminException.badRequest("状态变更原因必填");
        }
        if (!AppAdminConstants.STATUS_NORMAL.equals(targetStatus)
                && !AppAdminConstants.STATUS_DISABLED.equals(targetStatus))
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }
        requireManaged(userId);

        String current = mapper.selectStatus(userId);
        if (current == null)
        {
            throw AppAdminException.notFound();
        }
        if (current.equals(targetStatus))
        {
            // 幂等：状态已一致，不重复更新、不重复吊销
            return false;
        }

        int affected = mapper.updateStatusIfMatch(userId, current, targetStatus, operator);
        if (affected == 0)
        {
            // 并发下状态已被他人改变，按冲突返回而非静默成功（USER-09）
            throw AppAdminException.conflict(AppAdminErrorCodes.STATE_CONFLICT);
        }

        // 禁用后旧会话必须立即失效；启用不产生新会话，无需吊销
        if (AppAdminConstants.STATUS_DISABLED.equals(targetStatus))
        {
            revocationService.revokeAllForUser(userId, "A4 admin disabled account");
        }
        return true;
    }

    /**
     * 事务替换 App 用户角色授权（契约 §5.1）。
     *
     * 只允许授权 app_grantable=1 且启用、未删除、非超级管理员的角色；
     * 去重后整体替换；成功后吊销全部 App 会话，使旧角色声明不能继续使用（USER-08）。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean grantRoles(Long userId, List<Long> roleIds, String reason, String operator)
    {
        if (reason == null || reason.isBlank())
        {
            throw AppAdminException.badRequest("角色授权原因必填");
        }
        if (roleIds == null || roleIds.isEmpty())
        {
            throw AppAdminException.badRequest("至少需要指定一个角色");
        }
        requireManaged(userId);

        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(roleIds));

        int nonGrantable = mapper.countNonGrantableRoles(distinct);
        if (nonGrantable > 0)
        {
            throw AppAdminException.conflict(AppAdminErrorCodes.ROLE_BOUNDARY_CONFLICT);
        }

        List<Long> current = mapper.selectRoleIds(userId);
        Set<Long> currentSet = new LinkedHashSet<>(current == null ? List.of() : current);
        if (currentSet.equals(new LinkedHashSet<>(distinct)))
        {
            // 幂等：等价请求不重复写入、不重复吊销
            return false;
        }

        mapper.deleteUserRoles(userId);
        mapper.insertUserRoles(userId, distinct);

        revocationService.revokeAllForUser(userId, "A4 admin changed roles");
        return true;
    }

    /**
     * 账号域闸门：不属于 01/02/03 或已删除时按「不存在」处理，
     * 对外不区分「不存在」与「非可管理账号」，避免泄露账号存在性。
     */
    private void requireManaged(Long userId)
    {
        if (userId == null)
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }
        Long managed = mapper.selectManagedUserId(userId);
        if (managed == null)
        {
            throw AppAdminException.notFound();
        }
    }

    /** 批量装配角色编码，避免逐行查询造成 N+1。 */
    private Map<Long, List<String>> loadRoleCodes(List<Long> userIds)
    {
        Map<Long, List<String>> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty())
        {
            return result;
        }
        List<AppUserRoleRow> rows = mapper.selectRoleCodesByUserIds(userIds);
        if (rows == null)
        {
            return result;
        }
        for (AppUserRoleRow row : rows)
        {
            result.computeIfAbsent(row.getUserId(), k -> new ArrayList<>()).add(row.getRoleCode());
        }
        return result;
    }

    /** 参数净化：白名单排序 + 移除未知键，避免客户端字段名进入 SQL。 */
    private Map<String, Object> sanitize(Map<String, Object> params)
    {
        Map<String, Object> safe = new HashMap<>();
        if (params == null)
        {
            return safe;
        }
        for (Map.Entry<String, Object> e : params.entrySet())
        {
            if (e.getKey() == null || e.getKey().startsWith("orderBy") || "isAsc".equals(e.getKey()))
            {
                continue;
            }
            safe.put(e.getKey(), e.getValue());
        }
        String requested = params.get("orderByColumn") == null ? null : String.valueOf(params.get("orderByColumn"));
        String column = ORDER_WHITELIST.get(requested);
        if (column != null)
        {
            safe.put("orderByColumn", column);
            safe.put("isAsc", "desc".equalsIgnoreCase(String.valueOf(params.get("isAsc"))) ? "DESC" : "ASC");
        }
        return safe;
    }

    /** 供批量装配复用的实名状态读取（列表已由子查询带回，此处保留给详情扩展）。 */
    public Map<Long, String> realNameStatuses(List<Long> userIds)
    {
        Map<Long, String> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty())
        {
            return result;
        }
        List<AppUserRealNameRow> rows = mapper.selectRealNameStatusByUserIds(userIds);
        if (rows != null)
        {
            for (AppUserRealNameRow row : rows)
            {
                result.put(row.getUserId(), row.getStatus());
            }
        }
        return result;
    }

    /** 供批量装配复用的作者能力读取。 */
    public Map<Long, Boolean> authorCapabilities(List<Long> userIds)
    {
        Map<Long, Boolean> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty())
        {
            return result;
        }
        List<AppUserCapabilityRow> rows = mapper.selectAuthorCapabilityByUserIds(userIds);
        if (rows != null)
        {
            for (AppUserCapabilityRow row : rows)
            {
                result.put(row.getUserId(), row.getEnabled());
            }
        }
        return result;
    }
}
