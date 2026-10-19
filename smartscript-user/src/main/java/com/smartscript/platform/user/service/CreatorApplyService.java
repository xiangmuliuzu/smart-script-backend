package com.smartscript.platform.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.UserCreatorApplication;
import com.smartscript.platform.user.dto.CreatorApplyStatusDto;
import com.smartscript.platform.user.dto.CreatorApplySubmitRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;

/**
 * A5 创作者申请（契约 §1.6）。
 *
 * 状态机（与实名申请一致、与 A4 常量同集合）：
 *   NOT_SUBMITTED -> PENDING -> APPROVED
 *   PENDING -> REJECTED -> PENDING（驳回后重新提交）
 *
 * 强制边界：
 *   - 审核中或已开通能力时不可重复提交；历史已通过且能力被显式关闭时恢复申请入口。
 *   - 重提走新增行，保留完整审计链，不改写历史审核结果。
 *   - 通过后是否真正开通「作者能力」由 A4 审核端在决定时调用 AuthorCapabilityService 完成，
 *     本服务只写申请记录、不触碰 user_author_capability 与 sys_user。
 */
@Service
public class CreatorApplyService
{
    /** 状态：未提交（无申请记录时的对外取值）。 */
    public static final String STATUS_NOT_SUBMITTED = "NOT_SUBMITTED";
    /** 状态：审核中。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：已通过。 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 状态：已驳回。 */
    public static final String STATUS_REJECTED = "REJECTED";

    private static final int PEN_NAME_MAX = 50;
    private static final int DIRECTION_MAX = 200;
    private static final int PORTFOLIO_MAX = 500;
    private static final int INTRO_MAX = 500;
    private static final int CONTACT_MAX = 100;

    private final AppUserCenterMapper centerMapper;

    public CreatorApplyService(AppUserCenterMapper centerMapper)
    {
        this.centerMapper = centerMapper;
    }

    /** 查询创作者申请状态；从未提交过时返回 NOT_SUBMITTED 占位而不是 404。 */
    public CreatorApplyStatusDto getStatus(Long userId)
    {
        UserCreatorApplication latest = centerMapper.selectLatestCreatorApplication(userId);
        CreatorApplyStatusDto dto = new CreatorApplyStatusDto();
        if (latest == null)
        {
            dto.setStatus(STATUS_NOT_SUBMITTED);
            return dto;
        }
        // 保留历史审核记录；能力被显式关闭后，当前申请入口恢复为可申请状态。
        dto.setStatus(effectiveStatus(latest.getStatus(), centerMapper.selectCreatorCapabilityEnabled(userId)));
        dto.setPenName(latest.getPenName());
        dto.setDirection(latest.getDirection());
        dto.setPortfolio(latest.getPortfolio());
        dto.setIntro(latest.getIntro());
        dto.setContact(latest.getContact());
        dto.setRejectReason(latest.getRejectReason());
        dto.setSubmittedAt(latest.getSubmittedAt());
        dto.setReviewedAt(latest.getReviewedAt());
        return dto;
    }

    /** 提交申请。状态不符时被状态机拒绝（409）。 */
    @Transactional
    public CreatorApplyStatusDto submit(Long userId, CreatorApplySubmitRequest request)
    {
        if (request == null)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "no payload");
        }
        String penName = required(request.getPenName(), PEN_NAME_MAX, "pen name");
        String direction = required(request.getDirection(), DIRECTION_MAX, "direction");
        String intro = required(request.getIntro(), INTRO_MAX, "intro");
        String contact = required(request.getContact(), CONTACT_MAX, "contact");
        String portfolio = optional(request.getPortfolio(), PORTFOLIO_MAX, "portfolio");

        String current = centerMapper.selectLatestCreatorApplicationStatus(userId);
        Boolean enabled = centerMapper.selectCreatorCapabilityEnabled(userId);
        if (Boolean.TRUE.equals(enabled))
        {
            throw new AppAuthException(AppUserErrorCodes.CREATOR_APPLY_CONFLICT, 409,
                    AppUserErrorCodes.CREATOR_APPLY_CONFLICT_TEXT);
        }
        requireSubmittable(effectiveStatus(current, enabled));

        UserCreatorApplication record = new UserCreatorApplication();
        record.setUserId(userId);
        record.setPenName(penName);
        record.setDirection(direction);
        record.setPortfolio(portfolio);
        record.setIntro(intro);
        record.setContact(contact);
        record.setStatus(STATUS_PENDING);
        centerMapper.insertCreatorApplication(record);
        return getStatus(userId);
    }

    /** 显式关闭能力后恢复申请入口，历史审核结果仍保留在原记录中。 */
    private static String effectiveStatus(String status, Boolean enabled)
    {
        return STATUS_APPROVED.equals(status) && Boolean.FALSE.equals(enabled)
                ? STATUS_NOT_SUBMITTED : status;
    }

    /** 当前有效状态只有未申请或已驳回时允许提交。 */
    static void requireSubmittable(String currentStatus)
    {
        if (STATUS_PENDING.equals(currentStatus) || STATUS_APPROVED.equals(currentStatus))
        {
            throw new AppAuthException(AppUserErrorCodes.CREATOR_APPLY_CONFLICT, 409,
                    AppUserErrorCodes.CREATOR_APPLY_CONFLICT_TEXT);
        }
    }

    private static String required(String raw, int max, String field)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > max)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, field + " invalid");
        }
        return value;
    }

    private static String optional(String raw, int max, String field)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > max)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, field + " too long");
        }
        return value;
    }
}
