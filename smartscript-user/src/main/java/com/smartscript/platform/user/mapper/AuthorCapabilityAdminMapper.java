package com.smartscript.platform.user.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.admin.AuthorCapability;

/**
 * A4 PC 管理域：作者能力（契约 §3.3 / §5.3）。
 *
 * 强制边界：
 *   - user_author_capability 按 user_id 唯一（uk_user_author_capability_user）。
 *   - 本 Mapper **不含任何 sys_user.user_type 写操作**：作者能力变更不得隐式
 *     改写账号类型。CREATOR-04 直接针对这一点。
 *   - 操作人来自 PC 安全上下文，由服务层传入，不接受请求体指定。
 */
public interface AuthorCapabilityAdminMapper
{
    /**
     * 作者能力分页查询（联合 sys_user 取昵称与掩码手机号）。
     *
     * 支持的参数键：keyword、enabled、orderByColumn、isAsc。
     */
    List<AuthorCapability> selectCapabilityPage(Map<String, Object> params);

    /** 单个用户当前能力状态；无记录返回 null。 */
    AuthorCapability selectCapabilityByUserId(@Param("userId") Long userId);

    /** 当前 enabled 值，用于幂等判定。无记录返回 null。 */
    Boolean selectEnabledByUserId(@Param("userId") Long userId);

    /**
     * 幂等写入：按 user_id 唯一键做 upsert。
     * 重复设置同一状态时结果一致，不产生重复记录。
     */
    int upsertCapability(@Param("userId") Long userId,
                         @Param("enabled") boolean enabled,
                         @Param("reason") String reason,
                         @Param("operatorId") Long operatorId,
                         @Param("updateBy") String updateBy);
}
