package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppAdminErrorCodes;
import com.smartscript.platform.user.domain.admin.AuthorCapability;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.AppUserAdminMapper;
import com.smartscript.platform.user.mapper.AuthorCapabilityAdminMapper;

/**
 * A4 作者能力（契约 §5.3）。
 *
 * 职责：作者能力开关、原因与操作记录。
 *
 * 强制边界：
 *   - user_author_capability 按 user_id 唯一；重复设置同一状态幂等，不重复插入（CREATOR-03）。
 *   - **本服务不修改 sys_user.user_type**。作者能力与账号类型是两个独立维度，
 *     契约明确禁止隐式改写账号类型（CREATOR-04）。
 *   - reason 必填并限制长度。
 *   - 操作人来自 PC 安全上下文，不接受请求体指定。
 *   - 目标必须属于 App 账号域，否则按不存在处理。
 */
@Service
public class AuthorCapabilityService
{
    private final AuthorCapabilityAdminMapper mapper;
    private final AppUserAdminMapper appUserAdminMapper;

    /** 原因长度上限，与 user_author_capability.reason 列宽一致。 */
    private static final int REASON_MAX_LENGTH = 255;

    private static final Map<String, String> ORDER_WHITELIST;

    static
    {
        Map<String, String> m = new HashMap<>();
        m.put("userId", "u.user_id");
        m.put("updatedAt", "uac.operated_at");
        m.put("enabled", "enabled");
        ORDER_WHITELIST = Collections.unmodifiableMap(m);
    }

    public AuthorCapabilityService(AuthorCapabilityAdminMapper mapper, AppUserAdminMapper appUserAdminMapper)
    {
        this.mapper = mapper;
        this.appUserAdminMapper = appUserAdminMapper;
    }

    /** 分页：以 App 账号域用户为主，未设置过能力的用户按 enabled=0 列出。 */
    public List<AuthorCapability> page(Map<String, Object> params)
    {
        List<AuthorCapability> rows = mapper.selectCapabilityPage(sanitize(params));
        return rows == null ? new ArrayList<>() : rows;
    }

    public AuthorCapability detail(Long userId)
    {
        requireManaged(userId);
        AuthorCapability row = mapper.selectCapabilityByUserId(userId);
        if (row == null)
        {
            throw AppAdminException.notFound();
        }
        return row;
    }

    /**
     * 开通/关闭作者能力（契约 §5.3）。
     *
     * 重复设置同一状态幂等（返回 false 表示未发生变更）；
     * 全程不触碰 sys_user，尤其不改写 user_type。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean setEnabled(Long userId, boolean enabled, String reason, Long operatorId, String operatorName)
    {
        requireManaged(userId);

        if (reason == null || reason.isBlank())
        {
            throw AppAdminException.badRequest("作者能力变更原因必填");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > REASON_MAX_LENGTH)
        {
            throw AppAdminException.badRequest("作者能力变更原因超出长度上限");
        }

        Boolean current = mapper.selectEnabledByUserId(userId);
        if (current != null && current == enabled)
        {
            // 幂等：状态已一致，不重复写入、不刷新操作时间
            return false;
        }

        mapper.upsertCapability(userId, enabled, trimmed, operatorId, operatorName);
        return true;
    }

    /**
     * 账号域闸门。这里只读 sys_user 判定归属，绝不写入 sys_user。
     */
    private void requireManaged(Long userId)
    {
        if (userId == null)
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }
        if (appUserAdminMapper.selectManagedUserId(userId) == null)
        {
            throw AppAdminException.notFound();
        }
    }

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
}
