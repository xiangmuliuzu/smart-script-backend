package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.constant.AppAdminErrorCodes;
import com.smartscript.platform.user.domain.admin.CreatorApplication;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.CreatorApplicationAdminMapper;

/**
 * A4 创作者申请审核（契约 §5.6）。
 *
 * 职责：申请分页、详情、一次性审核决定；**通过时自动开通作者能力**。
 *
 * 强制边界：
 *   - 状态机只允许 PENDING -> APPROVED/REJECTED；条件更新保证同一申请只能决定一次，
 *     并发审核只有一个成功，另一个返回 409。
 *   - 驳回必须给出原因，通过时原因必须为空。
 *   - 通过时在同一事务内调用 {@link AuthorCapabilityService#setEnabled} 开通作者能力（幂等）；
 *     该调用**不修改 sys_user.user_type**，账号类型与作者能力始终是两个独立维度。
 *   - 决定提交后向申请人投递站内通知；投递失败不影响审核结果。
 */
@Service
public class CreatorApplicationReviewService
{
    private static final Logger log = LoggerFactory.getLogger(CreatorApplicationReviewService.class);

    /** 创作者审核结果站内通知的幂等键前缀：同一申请只投递一次。 */
    private static final String CREATOR_NOTICE_ID_PREFIX = "creatorapply-review-";

    private final CreatorApplicationAdminMapper mapper;
    private final UserNotificationAdminService notifications;
    private final AuthorCapabilityService authorCapabilityService;

    private static final Map<String, String> ORDER_WHITELIST;

    static
    {
        Map<String, String> m = new HashMap<>();
        m.put("applicationId", "uca.id");
        m.put("submittedAt", "uca.create_time");
        m.put("reviewedAt", "uca.audited_at");
        m.put("status", "uca.status");
        ORDER_WHITELIST = Collections.unmodifiableMap(m);
    }

    public CreatorApplicationReviewService(CreatorApplicationAdminMapper mapper,
            UserNotificationAdminService notifications, AuthorCapabilityService authorCapabilityService)
    {
        this.mapper = mapper;
        this.notifications = notifications;
        this.authorCapabilityService = authorCapabilityService;
    }

    /** 列表。 */
    public List<CreatorApplication> page(Map<String, Object> params)
    {
        List<CreatorApplication> rows = mapper.selectApplicationPage(sanitize(params));
        return rows == null ? new ArrayList<>() : rows;
    }

    /** 详情：不存在时 404。 */
    public CreatorApplication detail(Long applicationId)
    {
        if (applicationId == null)
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }
        CreatorApplication detail = mapper.selectApplicationDetail(applicationId);
        if (detail == null)
        {
            throw AppAdminException.notFound();
        }
        return detail;
    }

    /**
     * 一次性审核决定（契约 §5.6）。
     *
     * decision 只接受 APPROVE/REJECT；rejectReason 驳回必填、通过必须为空；
     * expectedStatus 固定 PENDING —— 客户端传入时必须是 PENDING，否则 409。
     *
     * 通过时在同一事务内开通作者能力：能力写入失败（极少见的系统错误）将使整个
     * 决定回滚，避免出现「申请已通过但能力未开通」的半成品状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public CreatorApplication decide(Long applicationId, String decision, String rejectReason, Long auditorId,
            String auditorName, String clientExpectedStatus)
    {
        if (applicationId == null)
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }
        if (!AppAdminConstants.DECISION_APPROVE.equals(decision)
                && !AppAdminConstants.DECISION_REJECT.equals(decision))
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }

        String expectedStatus = resolveExpectedStatus(clientExpectedStatus);

        boolean reject = AppAdminConstants.DECISION_REJECT.equals(decision);
        String trimmedReason = rejectReason == null ? null : rejectReason.trim();

        if (reject)
        {
            if (trimmedReason == null || trimmedReason.isEmpty())
            {
                throw AppAdminException.badRequest("驳回原因必填");
            }
            if (trimmedReason.length() > 500)
            {
                throw AppAdminException.badRequest("驳回原因超出长度上限");
            }
        }
        else if (trimmedReason != null && !trimmedReason.isEmpty())
        {
            throw AppAdminException.badRequest("通过时不得携带驳回原因");
        }

        String current = mapper.selectApplicationStatus(applicationId);
        if (current == null)
        {
            throw AppAdminException.notFound();
        }
        if (!expectedStatus.equals(current))
        {
            throw AppAdminException.conflict(AppAdminErrorCodes.STATE_CONFLICT);
        }

        String target = reject ? AppAdminConstants.REAL_NAME_REJECTED : AppAdminConstants.REAL_NAME_APPROVED;
        int affected = mapper.decideIfPending(applicationId, expectedStatus, target,
                auditorId, reject ? trimmedReason : null);
        if (affected == 0)
        {
            // 并发审核：另一个决定已抢先成功
            throw AppAdminException.conflict(AppAdminErrorCodes.STATE_CONFLICT);
        }

        Long applicantId = mapper.selectApplicationUserId(applicationId);
        if (!reject && applicantId != null)
        {
            // 通过即开通作者能力（同一事务，幂等；不修改 user_type）
            authorCapabilityService.setEnabled(applicantId, true,
                    "创作者申请审核通过（申请 #" + applicationId + "）", auditorId, auditorName);
        }

        notifyApplicantOfDecision(applicationId, applicantId, reject, trimmedReason);

        CreatorApplication updated = new CreatorApplication();
        updated.setApplicationId(applicationId);
        updated.setStatus(target);
        updated.setRejectReason(reject ? trimmedReason : null);
        return updated;
    }

    /**
     * 解析并校验客户端 expectedStatus。契约固定为 PENDING：其他值直接 409，
     * 而不是静默忽略后照常审核。未传时按 PENDING 处理，保持向后兼容。
     */
    private String resolveExpectedStatus(String clientExpectedStatus)
    {
        if (clientExpectedStatus == null || clientExpectedStatus.isBlank())
        {
            return AppAdminConstants.REAL_NAME_PENDING;
        }
        if (!AppAdminConstants.REAL_NAME_PENDING.equals(clientExpectedStatus))
        {
            throw AppAdminException.conflict(AppAdminErrorCodes.STATE_CONFLICT);
        }
        return AppAdminConstants.REAL_NAME_PENDING;
    }

    /**
     * 审核决定后向申请人投递站内通知（REVIEW 类型）。
     *
     * 在事务提交后投递：审核决定一旦回滚则不产生通知；投递失败只记日志，
     * 不影响审核本身已生效的结果。requestId 以申请号构造，配合唯一索引
     * 保证同一申请只投递一次。
     */
    private void notifyApplicantOfDecision(Long applicationId, Long applicantId, boolean reject, String trimmedReason)
    {
        if (notifications == null || applicantId == null)
        {
            return;
        }
        Runnable deliver = () -> deliverDecisionNotice(applicationId, applicantId, reject, trimmedReason);
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    deliver.run();
                }
            });
        }
        else
        {
            deliver.run();
        }
    }

    private void deliverDecisionNotice(Long applicationId, Long applicantId, boolean reject, String trimmedReason)
    {
        try
        {
            String title = reject ? "创作者申请未通过" : "创作者申请已通过";
            String content = reject
                    ? "很抱歉，您的创作者申请未通过。原因：" + (trimmedReason == null ? "" : trimmedReason)
                            + "。您可在「成为创作者」中修改后重新提交。"
                    : "恭喜，您的创作者申请已通过审核，现已开通创作者能力，可使用创作相关功能。";
            notifications.create(
                    CREATOR_NOTICE_ID_PREFIX + applicationId,
                    AppAdminConstants.NOTIFICATION_REVIEW,
                    title,
                    content,
                    "CREATOR_APPLY",
                    String.valueOf(applicationId),
                    List.of(applicantId),
                    "system");
        }
        catch (Exception e)
        {
            // 审核已生效，通知只是附带动作：失败仅记录，不回滚、不抛出
            log.warn("creator-apply decision notice skipped: {}", e.getClass().getSimpleName());
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
}
