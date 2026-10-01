package com.smartscript.platform.user.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.admin.AdminFeedback;

/**
 * A4 PC 管理域：用户反馈（契约 §3.5 / §5.5）。
 *
 * 强制边界：
 *   - 列表查询不选择 attachment_ref；附件只在详情按权限返回。
 *   - 状态机：SUBMITTED -> PROCESSING -> REPLIED/CLOSED，REPLIED -> CLOSED。
 *     由条件更新（status = expectedStatus）保证并发只有一个成功，
 *     非法回退由 SQL WHERE 直接排除并返回 0 行，调用方转 409。
 *   - 处理人来自 PC 安全上下文，由服务层传入。
 *   - A4 迁移已把存量 OPEN 归一为 SUBMITTED，DB CHECK 约束兜底状态集合。
 */
public interface AdminFeedbackMapper
{
    /**
     * 反馈分页查询。分页由 PageHelper 驱动。
     *
     * 支持的参数键：status、category、keyword、beginTime、endTime、
     * orderByColumn、isAsc。不返回 attachmentRefs。
     */
    List<AdminFeedback> selectFeedbackPage(Map<String, Object> params);

    /** 反馈详情（含 attachment_ref，由服务层按权限转为短时引用）。不存在返回 null。 */
    AdminFeedback selectFeedbackDetail(@Param("feedbackId") Long feedbackId);

    /** 读取当前状态，用于 expectedStatus 预校验与幂等判定。 */
    String selectFeedbackStatus(@Param("feedbackId") Long feedbackId);

    /**
     * 读取既有回复正文，用于 REPLY 的等价性比较。
     * 仅比较正文而不比较状态是不够的：内容不同的再次回复不应被当作等价重试。
     */
    String selectFeedbackReply(@Param("feedbackId") Long feedbackId);

    /**
     * 受理：SUBMITTED -> PROCESSING。
     * 仅当 status 在允许集合内时生效；重复等价操作返回 0 行但状态已一致，
     * 由服务层判定为幂等成功。
     */
    int acceptIfAllowed(@Param("feedbackId") Long feedbackId,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("allowedFrom") List<String> allowedFrom,
                        @Param("handlerId") Long handlerId);

    /** 回复：SUBMITTED/PROCESSING -> REPLIED，reply 必填（服务层校验）。 */
    int replyIfAllowed(@Param("feedbackId") Long feedbackId,
                       @Param("expectedStatus") String expectedStatus,
                       @Param("allowedFrom") List<String> allowedFrom,
                       @Param("reply") String reply,
                       @Param("handlerId") Long handlerId);

    /** 关闭：SUBMITTED/PROCESSING/REPLIED -> CLOSED。CLOSED 不可回退。 */
    int closeIfAllowed(@Param("feedbackId") Long feedbackId,
                       @Param("expectedStatus") String expectedStatus,
                       @Param("allowedFrom") List<String> allowedFrom,
                       @Param("handlerId") Long handlerId);
}
