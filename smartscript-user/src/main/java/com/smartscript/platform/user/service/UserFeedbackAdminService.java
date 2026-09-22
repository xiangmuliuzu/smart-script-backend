package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.constant.AppAdminErrorCodes;
import com.smartscript.platform.user.domain.admin.AdminFeedback;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.AdminFeedbackMapper;

/**
 * A4 用户反馈处理（契约 §5.5）。
 *
 * 职责：反馈分页、受理、回复、关闭状态机与附件安全访问。
 *
 * 状态机（与 DB CHECK 约束 ck_user_feedback_status 同集合）：
 *   SUBMITTED            -> PROCESSING            (ACCEPT)
 *   SUBMITTED/PROCESSING -> REPLIED               (REPLY，reply 必填)
 *   SUBMITTED/PROCESSING/REPLIED -> CLOSED        (CLOSE)
 *
 * 强制边界：
 *   - CLOSED 不在任何 allowedFrom 中，故不可回退；非法回退返回 409（FB-04）。
 *   - 并发/expectedStatus 不匹配时只有一个更新成功，其余 409（FB-05）。
 *   - 重复等价操作幂等。
 *   - 处理人来自 PC 安全上下文，不接受请求体指定。
 *   - 列表不返回附件短时地址；详情才按权限返回（FB-01、FB-06）。
 */
@Service
public class UserFeedbackAdminService
{
    private final AdminFeedbackMapper mapper;
    private final MaterialAccessTokenService materialTokenService;

    /** 回复长度上限，与列宽一致。 */
    private static final int REPLY_MAX_LENGTH = 2000;

    private static final List<String> ACCEPT_FROM =
            List.of(AppAdminConstants.FEEDBACK_SUBMITTED);
    private static final List<String> REPLY_FROM =
            List.of(AppAdminConstants.FEEDBACK_SUBMITTED, AppAdminConstants.FEEDBACK_PROCESSING);
    private static final List<String> CLOSE_FROM =
            List.of(AppAdminConstants.FEEDBACK_SUBMITTED, AppAdminConstants.FEEDBACK_PROCESSING,
                    AppAdminConstants.FEEDBACK_REPLIED);

    private static final Map<String, String> ORDER_WHITELIST;

    static
    {
        Map<String, String> m = new HashMap<>();
        m.put("feedbackId", "f.id");
        m.put("submittedAt", "f.create_time");
        m.put("handledAt", "f.handled_at");
        m.put("status", "f.status");
        ORDER_WHITELIST = Collections.unmodifiableMap(m);
    }

    public UserFeedbackAdminService(AdminFeedbackMapper mapper, MaterialAccessTokenService materialTokenService)
    {
        this.mapper = mapper;
        this.materialTokenService = materialTokenService;
    }

    /** 列表：不返回 attachmentRefs。 */
    public List<AdminFeedback> page(Map<String, Object> params)
    {
        List<AdminFeedback> rows = mapper.selectFeedbackPage(sanitize(params));
        return rows == null ? new ArrayList<>() : rows;
    }

    /**
     * 详情：按权限返回附件短时引用。
     * includeAttachment=false 时（例如只有列表权限）不返回任何附件引用。
     */
    public AdminFeedback detail(Long feedbackId, boolean includeAttachment)
    {
        if (feedbackId == null)
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }
        AdminFeedback detail = mapper.selectFeedbackDetail(feedbackId);
        if (detail == null)
        {
            throw AppAdminException.notFound();
        }
        if (includeAttachment)
        {
            detail.setAttachmentRefs(toShortLivedRefs(detail.getAttachmentRef()));
        }
        else
        {
            detail.setAttachmentRefs(new ArrayList<>());
        }
        return detail;
    }

    /**
     * 处理反馈（契约 §5.5）。
     *
     * action 与 expectedStatus 同时参与判定：SQL 用 allowedFrom + expectedStatus 双条件更新，
     * 因此并发下只有一个成功，非法回退（如 CLOSED -> PROCESSING）必然 0 行。
     */
    @Transactional(rollbackFor = Exception.class)
    public HandleResult handle(Long feedbackId, String action, String reply, String expectedStatus, Long handlerId)
    {
        if (feedbackId == null)
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }
        if (!AppAdminConstants.FEEDBACK_ACTION_ACCEPT.equals(action)
                && !AppAdminConstants.FEEDBACK_ACTION_REPLY.equals(action)
                && !AppAdminConstants.FEEDBACK_ACTION_CLOSE.equals(action))
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }

        String current = mapper.selectFeedbackStatus(feedbackId);
        if (current == null)
        {
            throw AppAdminException.notFound();
        }

        // expectedStatus 由客户端用于乐观并发控制；缺省时按当前状态处理
        if (expectedStatus != null && !expectedStatus.isBlank() && !expectedStatus.equals(current))
        {
            throw AppAdminException.conflict(AppAdminErrorCodes.STATE_CONFLICT);
        }

        String trimmedReply = reply == null ? null : reply.trim();
        if (AppAdminConstants.FEEDBACK_ACTION_REPLY.equals(action))
        {
            if (trimmedReply == null || trimmedReply.isEmpty())
            {
                throw AppAdminException.badRequest("回复内容必填");
            }
            if (trimmedReply.length() > REPLY_MAX_LENGTH)
            {
                throw AppAdminException.badRequest("回复内容超出长度上限");
            }
        }

        // 幂等：重复等价操作时状态已到达目标，按成功返回但不重复写入
        if (isAlreadyAtTarget(feedbackId, action, current, trimmedReply))
        {
            return new HandleResult(feedbackId, current, false);
        }

        if (!allowedFrom(action).contains(current))
        {
            // 状态机外或已关闭后的回退
            throw AppAdminException.conflict(AppAdminErrorCodes.STATE_CONFLICT);
        }

        int affected;
        String target;
        if (AppAdminConstants.FEEDBACK_ACTION_ACCEPT.equals(action))
        {
            target = AppAdminConstants.FEEDBACK_PROCESSING;
            affected = mapper.acceptIfAllowed(feedbackId, current, ACCEPT_FROM, handlerId);
        }
        else if (AppAdminConstants.FEEDBACK_ACTION_REPLY.equals(action))
        {
            target = AppAdminConstants.FEEDBACK_REPLIED;
            affected = mapper.replyIfAllowed(feedbackId, current, REPLY_FROM, trimmedReply, handlerId);
        }
        else
        {
            target = AppAdminConstants.FEEDBACK_CLOSED;
            affected = mapper.closeIfAllowed(feedbackId, current, CLOSE_FROM, handlerId);
        }

        if (affected == 0)
        {
            // 并发下状态已被他人改变
            throw AppAdminException.conflict(AppAdminErrorCodes.STATE_CONFLICT);
        }
        return new HandleResult(feedbackId, target, true);
    }

    /**
     * 重复等价操作的幂等判定。
     *
     * REPLY 必须比较回复正文：仅凭「状态已是 REPLIED 且新回复非空」视为等价，
     * 会把「换一个内容再回复一次」误判为无变化并返回 changed=false，
     * 使调用方以为新内容已生效。因此内容不同不构成等价，交由状态机判定（REPLIED
     * 不在 REPLY 的 allowedFrom 中，最终返回 409）。
     */
    private boolean isAlreadyAtTarget(Long feedbackId, String action, String current, String reply)
    {
        if (AppAdminConstants.FEEDBACK_ACTION_ACCEPT.equals(action))
        {
            return AppAdminConstants.FEEDBACK_PROCESSING.equals(current);
        }
        if (AppAdminConstants.FEEDBACK_ACTION_CLOSE.equals(action))
        {
            return AppAdminConstants.FEEDBACK_CLOSED.equals(current);
        }
        if (!AppAdminConstants.FEEDBACK_REPLIED.equals(current))
        {
            return false;
        }
        // 已回复：仅当正文与既有回复完全一致时才认为是等价重试
        String existingReply = mapper.selectFeedbackReply(feedbackId);
        return existingReply != null && existingReply.equals(reply);
    }

    private List<String> allowedFrom(String action)
    {
        if (AppAdminConstants.FEEDBACK_ACTION_ACCEPT.equals(action))
        {
            return ACCEPT_FROM;
        }
        if (AppAdminConstants.FEEDBACK_ACTION_REPLY.equals(action))
        {
            return REPLY_FROM;
        }
        return CLOSE_FROM;
    }

    /**
     * 附件引用转短时地址。裁决 GAP-5：单值映射为零或单元素数组，不解析分隔符。
     */
    private List<String> toShortLivedRefs(String stored)
    {
        List<String> refs = new ArrayList<>(1);
        // 只返回不透明短时令牌，真实附件地址不下发；过期与一次性消费由服务端强制
        String token = materialTokenService.issue("FEEDBACK_ATTACHMENT", stored);
        if (token != null)
        {
            refs.add(token);
        }
        return refs;
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

    /** 处理结果：目标状态与是否实际发生变更。 */
    public static class HandleResult
    {
        private final Long feedbackId;
        private final String status;
        private final boolean changed;

        public HandleResult(Long feedbackId, String status, boolean changed)
        {
            this.feedbackId = feedbackId;
            this.status = status;
            this.changed = changed;
        }

        public Long getFeedbackId()
        {
            return feedbackId;
        }

        public String getStatus()
        {
            return status;
        }

        public boolean isChanged()
        {
            return changed;
        }
    }
}
