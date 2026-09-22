package com.smartscript.platform.user.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.admin.AdminNotification;
import com.smartscript.platform.user.domain.admin.NotificationReceiverBrief;

/**
 * A4 PC 管理域：用户消息（契约 §3.4 / §5.4）。
 *
 * 强制边界：
 *   - v1 只接受明确 userIds，不实现未定义的人群圈选规则。
 *   - 幂等键 request_id 唯一（uk_user_notification_request_id）：
 *     相同 requestId 的等价请求只创建一次；不同内容复用同一 requestId 返回 409。
 *   - 消息主体与收件记录同事务写入（由服务层 @Transactional 保证），
 *     部分失败不得返回整体成功。
 *   - receiverCount 由聚合 COUNT 派生，不设冗余列（裁决 GAP-4）。
 *   - 收件人必须存在且未删除的 App 用户，SQL 层校验。
 */
public interface AdminNotificationMapper
{
    /**
     * 消息分页查询。分页由 PageHelper 驱动。
     *
     * 支持的参数键：type、createdBy、beginTime、endTime、
     * orderByColumn、isAsc。返回 receiverCount 聚合值。
     */
    List<AdminNotification> selectNotificationPage(Map<String, Object> params);

    /** 消息详情（含脱敏收件人摘要）。不存在返回 null。 */
    AdminNotification selectNotificationDetail(@Param("notificationId") Long notificationId);

    /** 详情用：脱敏收件人列表。 */
    List<NotificationReceiverBrief> selectReceiverBriefs(@Param("notificationId") Long notificationId);

    /** 按幂等键读取既有消息，用于等价请求复用与冲突判定。 */
    AdminNotification selectByRequestId(@Param("requestId") String requestId);

    /** 读取既有消息的幂等签名，用于判定「等价重试」还是「同键不同内容」。 */
    String selectIdempotencySignature(@Param("requestId") String requestId);

    /** 写入消息主体，返回影响行数并回填主键。 */
    int insertNotification(AdminNotification notification);

    /** 收件人去重后批量写入；userIds 已由服务层校验为存在且未删除的 App 用户。 */
    int insertReceivers(@Param("notificationId") Long notificationId,
                        @Param("userIds") List<Long> userIds);

    /**
     * 校验收件人合法性：返回 userIds 中**不存在、已删除或不属于 App 域**的个数。
     * 非 0 时整体失败，不留下半成品（MSG-03）。
     */
    int countInvalidReceivers(@Param("userIds") List<Long> userIds);

    /** 收件人数，用于人数上限与批处理阈值判定。 */
    int countReceivers(@Param("notificationId") Long notificationId);
}
