package com.smartscript.platform.user.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.admin.CreatorApplication;

/**
 * A4 PC 管理域：创作者申请查询与一次性审核决定（契约 §4 / §5.6）。
 *
 * 强制边界：
 *   - 审核决定使用条件更新（status = expectedStatus），使同一申请并发审核只有一个成功，另一个返回 409。
 *   - 不修改 user_creator_application 列名；create_time/audited_at 在 XML 中映射为 submittedAt/reviewedAt，
 *     auditor_id 关联 sys_user 取 reviewerName。
 *   - 手机号只以掩码形式出库。
 */
public interface CreatorApplicationAdminMapper
{
    /**
     * 创作者申请分页查询。分页由 PageHelper 驱动。
     *
     * 支持的参数键：status、keyword、beginTime、endTime、orderByColumn、isAsc。
     */
    List<CreatorApplication> selectApplicationPage(Map<String, Object> params);

    /** 创作者申请详情；不存在时返回 null，调用方转 404。 */
    CreatorApplication selectApplicationDetail(@Param("applicationId") Long applicationId);

    /** 读取当前状态，用于 expectedStatus 预校验与幂等判定。 */
    String selectApplicationStatus(@Param("applicationId") Long applicationId);

    /** 读取申请所属用户，用于审核通过后开通作者能力。不存在返回 null。 */
    Long selectApplicationUserId(@Param("applicationId") Long applicationId);

    /**
     * 条件审核决定：仅当 status = expectedStatus 时生效，返回影响行数。
     * 返回 0 表示并发冲突或状态已变化，调用方转 409。
     */
    int decideIfPending(@Param("applicationId") Long applicationId,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("status") String status,
                        @Param("auditorId") Long auditorId,
                        @Param("rejectReason") String rejectReason);
}
