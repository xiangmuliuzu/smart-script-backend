package com.smartscript.platform.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.UserRealNameAuth;
import com.smartscript.platform.user.dto.RealNameStatusDto;
import com.smartscript.platform.user.dto.RealNameSubmitRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.integration.AliyunRealNameVerifier;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.util.AppMasks;

/**
 * A5 实名认证（契约 §1.3，规格 §8.4）。
 *
 * 状态机（与《A用户与认证开发规格》一致，与 A4 常量同集合）：
 *   NOT_SUBMITTED -> PENDING -> APPROVED
 *   PENDING -> REJECTED -> PENDING（驳回后重新提交）
 *   提交内通过阿里云二要素接口完成 PENDING -> APPROVED/REJECTED；通道未启用时拒绝提交。
 *
 * 强制边界：
 *   - 审核中（PENDING）不可重复提交，已通过（APPROVED）不可由用户自行覆盖，两者返回 409。
 *   - 只有 REJECTED 或 NOT_SUBMITTED 允许提交；重提走新增行，保留完整审计链。
 *   - 申请记录只保存姓名和证件号掩码，核验通过后姓名快照写入用户表用于合同。
 *   - 日志不记录姓名和证件号。
 */
@Service
public class AppRealNameService
{
    /** 状态：未提交（无申请记录时的对外取值）。 */
    public static final String STATUS_NOT_SUBMITTED = "NOT_SUBMITTED";
    /** 状态：审核中。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：已通过。 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 状态：已驳回。 */
    public static final String STATUS_REJECTED = "REJECTED";

    private static final int REAL_NAME_MIN = 2;
    private static final int REAL_NAME_MAX = 32;

    /** 身份证号形态：18 位，前 17 位为数字，末位可为 X/x。 */
    private static final String ID_NUMBER_PATTERN = "^[0-9]{17}[0-9Xx]$";

    private final AppUserCenterMapper centerMapper;
    private final AliyunRealNameVerifier verifier;
    private final RealNameReviewService reviewService;

    public AppRealNameService(AppUserCenterMapper centerMapper, AliyunRealNameVerifier verifier, RealNameReviewService reviewService)
    {
        this.centerMapper = centerMapper;
        this.verifier = verifier;
        this.reviewService = reviewService;
    }

    /** 查询实名状态；从未提交过时返回 NOT_SUBMITTED 占位而不是 404。 */
    public RealNameStatusDto getStatus(Long userId)
    {
        UserRealNameAuth latest = centerMapper.selectLatestRealName(userId);
        RealNameStatusDto dto = new RealNameStatusDto();
        if (latest == null)
        {
            dto.setStatus(STATUS_NOT_SUBMITTED);
            return dto;
        }
        dto.setStatus(latest.getStatus());
        dto.setRealNameMasked(latest.getRealNameMask());
        dto.setIdNumberMasked(latest.getIdNumberMask());
        dto.setRejectReason(latest.getRejectReason());
        dto.setSubmittedAt(latest.getSubmittedAt());
        dto.setReviewedAt(latest.getReviewedAt());
        return dto;
    }

    /** 首次提交。已有记录（含驳回）时按 resubmit 语义处理，避免前端多写一套判断。 */
    @Transactional
    public RealNameStatusDto submit(Long userId, RealNameSubmitRequest request)
    {
        return doSubmit(userId, request);
    }

    /** 驳回后重新提交。状态不符时与 submit 一样被状态机拒绝。 */
    @Transactional
    public RealNameStatusDto resubmit(Long userId, RealNameSubmitRequest request)
    {
        return doSubmit(userId, request);
    }

    private RealNameStatusDto doSubmit(Long userId, RealNameSubmitRequest request)
    {
        if (request == null)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "no payload");
        }
        String realName = normalizeRealName(request.getRealName());
        String idNumber = normalizeIdNumber(request.getIdNumber());

        // 串行处理该用户的并发提交，避免同时多次调用计费接口。
        centerMapper.lockRealNameApplicant(userId);
        String current = centerMapper.selectLatestRealNameStatus(userId);
        requireSubmittable(current);

        // 本地校验全部通过后才调用；网络异常中止申请，不伪造核验结果。
        String rejectReason = verifier.verify(realName, idNumber);

        UserRealNameAuth record = new UserRealNameAuth();
        record.setUserId(userId);
        // 申请记录只保存掩码；核验通过后另存合同所需的姓名快照。
        record.setRealNameMask(AppMasks.maskRealName(realName));
        record.setIdNumberMask(AppMasks.maskIdNumber(idNumber));
        record.setStatus(STATUS_PENDING);
        centerMapper.insertRealName(record);
        // 复用状态流转与提交后通知；运维端不再提供人工审核入口。
        reviewService.decide(record.getApplicationId(), rejectReason == null ? "APPROVE" : "REJECT",
                rejectReason, null, STATUS_PENDING);
        // 核验通过时把明文姓名快照写入 sys_user，供合同签署域渲染甲乙方真实姓名；
        // 证件号仍不落明文，user_real_name_auth 掩码链保持不变
        if (rejectReason == null)
        {
            centerMapper.markRealNameVerified(userId, realName);
        }
        return getStatus(userId);
    }

    /**
     * 状态机准入：PENDING 与 APPROVED 均拒绝，NOT_SUBMITTED 与 REJECTED 允许。
     *
     * 这两个分支的文案刻意相同：对用户而言「审核中不能再提交」与「已通过不能再提交」
     * 都是当前状态不允许，无需暴露历史审核细节。
     */
    static void requireSubmittable(String currentStatus)
    {
        if (STATUS_PENDING.equals(currentStatus) || STATUS_APPROVED.equals(currentStatus))
        {
            throw new AppAuthException(AppUserErrorCodes.REAL_NAME_CONFLICT, 409,
                    AppUserErrorCodes.REAL_NAME_CONFLICT_TEXT);
        }
    }

    static String normalizeRealName(String raw)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < REAL_NAME_MIN || value.length() > REAL_NAME_MAX)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "real name length invalid");
        }
        return value;
    }

    static String normalizeIdNumber(String raw)
    {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches(ID_NUMBER_PATTERN))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "id number format invalid");
        }
        return value;
    }

}
