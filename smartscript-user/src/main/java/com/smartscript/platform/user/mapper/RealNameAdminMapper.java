package com.smartscript.platform.user.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.admin.RealNameApplication;

/**
 * A4 PC 管理域：实名申请查询与一次性审核决定（契约 §4 / §5.2）。
 *
 * 强制边界：
 *   - 列表查询不选择 material_ref，材料只在详情按权限返回。
 *   - 审核决定使用条件更新（status = expectedStatus），
 *     使同一申请并发审核只有一个成功，另一个返回 409。
 *   - 不修改 user_real_name_auth 列名；create_time/audited_at 在 XML 中
 *     直接映射为 submittedAt/reviewedAt（裁决 GAP-6）。
 */
public interface RealNameAdminMapper
{
    /**
     * 实名申请分页查询。分页由 PageHelper 驱动。
     *
     * 支持的参数键：status、keyword、beginTime、endTime、
     * orderByColumn、isAsc。不返回 materialRefs。
     */
    List<RealNameApplication> selectApplicationPage(Map<String, Object> params);

    /**
     * 实名申请详情（含 material_ref，由服务层按权限决定是否转为短时引用）。
     * 不存在时返回 null，调用方转 404。
     */
    RealNameApplication selectApplicationDetail(@Param("applicationId") Long applicationId);

    /** 读取当前状态，用于 expectedStatus 预校验与幂等判定。 */
    String selectApplicationStatus(@Param("applicationId") Long applicationId);

    /**
     * 条件审核决定：仅当 status = expectedStatus 时生效，返回影响行数。
     * 返回 0 表示并发冲突或状态已变化，调用方转 409。
     */
    int decideIfPending(@Param("applicationId") Long applicationId,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("status") String status,
                        @Param("auditorId") Long auditorId,
                        @Param("rejectReason") String rejectReason);

    /** 同一用户是否存在 PENDING 申请，用于提交侧冲突判定。 */
    int countPendingByUser(@Param("userId") Long userId);
}
