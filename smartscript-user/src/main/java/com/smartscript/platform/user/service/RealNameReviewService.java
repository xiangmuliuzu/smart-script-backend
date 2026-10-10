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
import com.smartscript.platform.user.domain.admin.RealNameApplication;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.RealNameAdminMapper;

/**
 * A4 实名审核（契约 §5.2）。
 *
 * 职责：申请分页、详情、一次性审核决定与材料安全访问。
 *
 * 强制边界：
 *   - 状态机只允许 PENDING -> APPROVED/REJECTED；条件更新保证同一申请只能决定一次，
 *     并发审核只有一个成功，另一个返回 409（REAL-05）。
 *   - 驳回必须给出原因，通过时原因必须为空。
 *   - 材料引用只在详情按权限返回，且转换为短时引用；
 *     列表、日志与错误响应均不得携带永久材料地址（REAL-01/06、SEC-02）。
 *   - 列名到契约字段的映射在 Mapper XML 完成，不改实名表列名（裁决 GAP-6）。
 */
@Service
public class RealNameReviewService
{
    private final RealNameAdminMapper mapper;
    private final MaterialAccessTokenService materialTokenService;

    private static final Map<String, String> ORDER_WHITELIST;

    static
    {
        Map<String, String> m = new HashMap<>();
        m.put("applicationId", "rna.id");
        m.put("submittedAt", "rna.create_time");
        m.put("reviewedAt", "rna.audited_at");
        m.put("status", "rna.status");
        ORDER_WHITELIST = Collections.unmodifiableMap(m);
    }

    public RealNameReviewService(RealNameAdminMapper mapper, MaterialAccessTokenService materialTokenService)
    {
        this.mapper = mapper;
        this.materialTokenService = materialTokenService;
    }

    /** 列表：不返回 materialRefs（契约 §3.2）。 */
    public List<RealNameApplication> page(Map<String, Object> params)
    {
        List<RealNameApplication> rows = mapper.selectApplicationPage(sanitize(params));
        return rows == null ? new ArrayList<>() : rows;
    }

    /**
     * 详情：按权限返回短时材料引用。
     * 传入 includeMaterial=false 时不返回任何材料引用（用于只有列表权限的调用方）。
     */
    public RealNameApplication detail(Long applicationId, boolean includeMaterial)
    {
        if (applicationId == null)
        {
            throw AppAdminException.badRequest(AppAdminErrorCodes.INVALID_PARAM);
        }
        RealNameApplication detail = mapper.selectApplicationDetail(applicationId);
        if (detail == null)
        {
            throw AppAdminException.notFound();
        }
        if (includeMaterial)
        {
            detail.setMaterialRefs(toShortLivedRefs(detail.getMaterialRef()));
        }
        else
        {
            detail.setMaterialRefs(new ArrayList<>());
        }
        return detail;
    }

    /**
     * 一次性审核决定（契约 §5.2）。
     *
     * decision 只接受 APPROVE/REJECT；rejectReason 驳回必填、通过必须为空；
     * expectedStatus 固定 PENDING —— 客户端传入时必须是 PENDING，否则 409；
     * 未传时按契约取 PENDING。
     *
     * 前置状态读取与条件更新使用同一个 expected 值，因此并发审核只有一个成功（REAL-04/05），
     * 且携带错误 expectedStatus 的请求不会偶然成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public RealNameApplication decide(Long applicationId, String decision, String rejectReason, Long auditorId,
            String clientExpectedStatus)
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
            // 通过时必须为空，避免把驳回理由写进已通过的申请
            throw AppAdminException.badRequest("通过时不得携带驳回原因");
        }

        String current = mapper.selectApplicationStatus(applicationId);
        if (current == null)
        {
            throw AppAdminException.notFound();
        }
        if (!expectedStatus.equals(current))
        {
            // 客户端期望状态与实际不符：已决定、非 PENDING，或客户端传了错误值
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

        RealNameApplication updated = new RealNameApplication();
        updated.setApplicationId(applicationId);
        updated.setStatus(target);
        updated.setRejectReason(reject ? trimmedReason : null);
        return updated;
    }

    /**
     * 解析并校验客户端 expectedStatus。
     *
     * 契约固定为 PENDING：传入其他值属于客户端状态判断错误，直接 409，
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
     * 材料引用转为短时授权引用。
     *
     * 裁决 GAP-5：单值材料映射为零或单元素数组，**不解析分隔符**。
     * 这里对已存在的引用做一次性封装，使其不再是永久地址；真实网关签名由后续迭代接入。
     */
    private List<String> toShortLivedRefs(String stored)
    {
        List<String> refs = new ArrayList<>(1);
        // 只返回不透明短时令牌，真实材料地址不下发；过期与一次性消费由服务端强制
        String token = materialTokenService.issue("REAL_NAME_MATERIAL", stored);
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
}
