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
 *   - 审核中（PENDING）不可重复提交，审核通过（APPROVED，已是创作者）不可再申请，两者返回 409。
 *   - 只有 NOT_SUBMITTED 或 REJECTED 允许提交；重提走新增行，保留完整审计链。
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
        dto.setStatus(latest.getStatus());
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
        requireSubmittable(current);

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

    /**
     * 状态机准入：PENDING 与 APPROVED 均拒绝，NOT_SUBMITTED 与 REJECTED 允许。
     * 两者文案相同：对用户而言都是「当前状态不允许再提交」，无需暴露历史审核细节。
     */
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
