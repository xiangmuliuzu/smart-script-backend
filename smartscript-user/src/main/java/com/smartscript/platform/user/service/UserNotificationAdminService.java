package com.smartscript.platform.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.constant.AppAdminErrorCodes;
import com.smartscript.platform.user.domain.admin.AdminNotification;
import com.smartscript.platform.user.domain.admin.NotificationReceiverBrief;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.AdminNotificationMapper;

/**
 * A4 用户消息管理（契约 §5.4）。
 *
 * 职责：消息创建、显式收件人写入、查询与发送结果。
 *
 * 强制边界：
 *   - v1 只接受明确 userIds，不实现未定义的人群圈选规则。
 *   - requestId 为幂等键：相同 requestId 的等价请求只创建一次；
 *     不同内容复用同一 requestId 返回 409（MSG-02）。
 *   - 消息主体与收件记录同事务写入；收件人不合法时整体失败，不留半成品（MSG-03）。
 *   - 人数与内容有上限；超过同步阈值拒绝，不在 HTTP 请求内无上限循环（MSG-04）。
 *   - 收件人必须存在、未删除且属于 App 账号域。
 */
@Service
public class UserNotificationAdminService
{
    private final AdminNotificationMapper mapper;

    /** 单次创建收件人上限。 */
    private static final int MAX_RECEIVERS = 500;

    /**
     * 超过该值必须转受控批处理；v1 在同步接口中直接拒绝，不在 HTTP 请求内无上限循环。
     *
     * 取 50：远低于 500 硬上限，为单次同步请求留出确定的写入规模，
     * 同时使阈值在隔离库（App 账号域约 100 人）可被真实构造与验证。
     */
    private static final int SYNC_THRESHOLD = 50;

    /** 标题与正文长度上限，与列宽一致。 */
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int CONTENT_MAX_LENGTH = 2000;
    private static final int REQUEST_ID_MAX_LENGTH = 64;

    private static final Map<String, String> ORDER_WHITELIST;

    static
    {
        Map<String, String> m = new HashMap<>();
        m.put("notificationId", "n.id");
        m.put("createdAt", "n.create_time");
        m.put("type", "n.type");
        ORDER_WHITELIST = Collections.unmodifiableMap(m);
    }

    private final TransactionTemplate newTransactionTemplate;

    public UserNotificationAdminService(AdminNotificationMapper mapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager)
    {
        this.mapper = mapper;
        // REQUIRES_NEW 语义：独立于外层事务，用于冲突后的复查
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(
                TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.newTransactionTemplate.setReadOnly(true);
    }

    /** 分页：返回 receiverCount 聚合值与脱敏元信息，不含全量收件人。 */
    public List<AdminNotification> page(Map<String, Object> params)
    {
        List<AdminNotification> rows = mapper.selectNotificationPage(sanitize(params));
        return rows == null ? new ArrayList<>() : rows;
    }

    /** 详情：返回脱敏收件人摘要，不回传全量敏感用户资料。 */
    public AdminNotification detail(Long notificationId)
    {
        if (notificationId == null)
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }
        AdminNotification detail = mapper.selectNotificationDetail(notificationId);
        if (detail == null)
        {
            throw AppAdminException.notFound();
        }
        List<NotificationReceiverBrief> briefs = mapper.selectReceiverBriefs(notificationId);
        detail.setReceivers(briefs == null ? new ArrayList<>() : briefs);
        return detail;
    }

    /**
     * 创建消息（契约 §5.4）。
     *
     * 幂等语义：
     *   - 相同 requestId + 等价内容 -> 复用既有消息，返回既有结果（不重复创建）；
     *   - 相同 requestId + 不同内容 -> 409。
     *
     * 并发安全性：唯一索引 uk_user_notification_request_id 是最终仲裁者。
     * 「先查询后插入」在并发下会双空，落库阶段必然有一方触发唯一键冲突；
     * 该冲突被捕获后复查既有消息，等价则复用、不等价则 409，
     * 而不是把冲突冒泡成 500。这样并发等价重试同样只创建一次。
     */
    @Transactional(rollbackFor = Exception.class)
    public CreateResult create(String requestId, String type, String title, String content,
            String businessType, String businessId, List<Long> userIds, String operator)
    {
        validateCreateInput(requestId, type, title, content, businessType, businessId, userIds);

        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(userIds));

        String signature = signature(requestId, type, title, content, businessType, businessId, distinct);

        AdminNotification existing = mapper.selectByRequestId(requestId);
        if (existing != null)
        {
            return reuseOrConflict(existing, signature);
        }

        // 收件人合法性：不存在、已删除或非 App 域一律整体失败
        int invalid = mapper.countInvalidReceivers(distinct);
        if (invalid > 0)
        {
            throw AppAdminException.badRequest("存在不可作为收件人的用户");
        }

        AdminNotification entity = new AdminNotification();
        entity.setRequestId(requestId);
        entity.setType(type);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setBusinessType(businessType);
        entity.setBusinessId(businessId);
        entity.setCreatedBy(operator);
        try
        {
            mapper.insertNotification(entity);
        }
        catch (DuplicateKeyException e)
        {
            // 并发下另一个等价请求已先落库。
            // 此处必须用一个全新事务读取赢家：当前事务已被标记 rollback-only，
            // 在本事务内继续查询必然失败。用 TransactionTemplate 而不是同类的
            // @Transactional(REQUIRES_NEW) 方法——自调用不会经过代理，传播级别不会生效。
            return resolveConcurrentWinnerInNewTransaction(requestId, signature);
        }

        mapper.insertReceivers(entity.getNotificationId(), distinct);
        return new CreateResult(entity.getNotificationId(), distinct.size(), true);
    }

    /**
     * 唯一键冲突后的并发收敛：在全新事务中读取既有消息，
     * 等价则复用（不重复创建），不等价则 409。
     */
    private CreateResult resolveConcurrentWinnerInNewTransaction(String requestId, String signature)
    {
        CreateResult result = newTransactionTemplate.execute(status ->
        {
            AdminNotification winner = mapper.selectByRequestId(requestId);
            if (winner == null)
            {
                return null;
            }
            String storedSignature = mapper.selectIdempotencySignature(winner.getRequestId());
            return new CreateResult(winner.getNotificationId(), winner.getReceiverCount(), false,
                    storedSignature);
        });

        if (result == null)
        {
            // 极端情况：冲突行随后被删除。按系统错误返回，不猜测结果。
            throw new AppAdminException(AppAdminErrorCodes.SYSTEM_ERROR, 500,
                    AppAdminErrorCodes.SYSTEM_ERROR_TEXT);
        }
        if (result.getStoredSignature() != null && signature.equals(sha256(result.getStoredSignature())))
        {
            return new CreateResult(result.getNotificationId(), result.getReceiverCount(), false);
        }
        throw AppAdminException.conflict(AppAdminErrorCodes.IDEMPOTENCY_CONFLICT);
    }

    /** 既有消息与本次请求等价则复用，否则判定为幂等键冲突。 */
    private CreateResult reuseOrConflict(AdminNotification existing, String signature)
    {
        String storedSignature = mapper.selectIdempotencySignature(existing.getRequestId());
        if (storedSignature != null && signature.equals(sha256(storedSignature)))
        {
            return new CreateResult(existing.getNotificationId(), existing.getReceiverCount(), false);
        }
        throw AppAdminException.conflict(AppAdminErrorCodes.IDEMPOTENCY_CONFLICT);
    }

    private void validateCreateInput(String requestId, String type, String title, String content,
            String businessType, String businessId, List<Long> userIds)
    {
        if (requestId == null || requestId.isBlank() || requestId.length() > REQUEST_ID_MAX_LENGTH)
        {
            throw AppAdminException.badRequest("requestId 必填且长度受限");
        }
        if (type == null || !isKnownType(type))
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH)
        {
            throw AppAdminException.badRequest("标题必填且长度受限");
        }
        if (content == null || content.isBlank() || content.length() > CONTENT_MAX_LENGTH)
        {
            throw AppAdminException.badRequest("正文必填且长度受限");
        }
        // businessType 与 businessId 必须成对出现，避免只写一半的业务引用
        boolean hasType = businessType != null && !businessType.isBlank();
        boolean hasId = businessId != null && !businessId.isBlank();
        if (hasType != hasId)
        {
            throw AppAdminException.badRequest("businessType 与 businessId 必须同时提供");
        }
        if (hasType && (businessType.length() > 64 || businessId.length() > 64))
        {
            throw AppAdminException.badRequest("业务引用超出长度上限");
        }
        if (userIds == null || userIds.isEmpty())
        {
            throw AppAdminException.badRequest("收件人列表必填");
        }
        long distinctCount = new LinkedHashSet<>(userIds).size();
        if (distinctCount > MAX_RECEIVERS)
        {
            throw AppAdminException.badRequest("收件人数超出上限");
        }
        if (distinctCount > SYNC_THRESHOLD)
        {
            // v1 不接受无上限同步循环，超阈值明确拒绝而不是静默逐条写入
            throw AppAdminException.badRequest("收件人数超过同步阈值，请使用批处理通道");
        }
    }

    private boolean isKnownType(String type)
    {
        return AppAdminConstants.NOTIFICATION_SYSTEM.equals(type)
                || AppAdminConstants.NOTIFICATION_REVIEW.equals(type)
                || AppAdminConstants.NOTIFICATION_TRANSACTION.equals(type)
                || AppAdminConstants.NOTIFICATION_BENEFIT.equals(type);
    }

    /**
     * 幂等签名，需与 Mapper 中 SQL 侧 SHA2 计算口径完全一致，
     * 否则等价请求会被误判为冲突。
     */
    private String signature(String requestId, String type, String title, String content,
            String businessType, String businessId, List<Long> distinctUserIds)
    {
        List<Long> sorted = new ArrayList<>(distinctUserIds);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(type)).append('|')
          .append(nullToEmpty(title)).append('|')
          .append(nullToEmpty(content)).append('|')
          .append(nullToEmpty(businessType)).append('|')
          .append(nullToEmpty(businessId)).append('|');
        for (int i = 0; i < sorted.size(); i++)
        {
            if (i > 0)
            {
                sb.append(',');
            }
            sb.append(sorted.get(i));
        }
        return sha256(sb.toString());
    }

    private String nullToEmpty(String s)
    {
        return s == null ? "" : s;
    }

    private String sha256(String input)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest)
            {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 unavailable", e);
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
            safe.put("isAsc", "asc".equalsIgnoreCase(String.valueOf(params.get("isAsc"))) ? "ASC" : "DESC");
        }
        return safe;
    }

    /** 创建结果：是否新建、消息主键与收件人数。 */
    public static class CreateResult
    {
        private final Long notificationId;
        private final Integer receiverCount;
        private final boolean created;
        /** 仅用于冲突复查时把既有签名带出事务，不参与对外序列化。 */
        private final String storedSignature;

        public CreateResult(Long notificationId, Integer receiverCount, boolean created)
        {
            this(notificationId, receiverCount, created, null);
        }

        public CreateResult(Long notificationId, Integer receiverCount, boolean created, String storedSignature)
        {
            this.notificationId = notificationId;
            this.receiverCount = receiverCount;
            this.created = created;
            this.storedSignature = storedSignature;
        }

        public String getStoredSignature()
        {
            return storedSignature;
        }

        public Long getNotificationId()
        {
            return notificationId;
        }

        public Integer getReceiverCount()
        {
            return receiverCount;
        }

        public boolean isCreated()
        {
            return created;
        }
    }
}
