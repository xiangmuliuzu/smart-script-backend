package com.smartscript.platform.user.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.UserRealNameAuth;
import com.smartscript.platform.user.dto.RealNameStatusDto;
import com.smartscript.platform.user.dto.RealNameSubmitRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.util.AppMasks;

/**
 * A5 实名认证（契约 §1.3，规格 §8.4）。
 *
 * 状态机（与《A用户与认证开发规格》一致，与 A4 常量同集合）：
 *   NOT_SUBMITTED -> PENDING -> APPROVED
 *   PENDING -> REJECTED -> PENDING（驳回后重新提交）
 *
 * 强制边界：
 *   - 审核中（PENDING）不可重复提交，已通过（APPROVED）不可由用户自行覆盖，两者返回 409。
 *   - 只有 REJECTED 或 NOT_SUBMITTED 允许提交；重提走新增行，保留完整审计链。
 *   - 明文姓名与证件号**不落库**：只写 {real_name_mask, id_number_mask}，
 *     与 A4 审核端读取的掩码列一致（A4 不改列名、不解析明文）。
 *   - 材料引用沿用平台上传服务的返回地址；本服务不签发对外地址，
 *     审核端读取材料仍走 A4 的短时授权链路。
 *   - 日志不记录姓名、证件号与材料地址。
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
    private static final int MATERIAL_REFS_MAX = 3;
    private static final int MATERIAL_REF_MAX_LENGTH = 255;

    /** 身份证号形态：15 位旧证 / 18 位新证（末位可为 X/x）。 */
    private static final String ID_NUMBER_PATTERN = "^\\d{15}|\\d{17}[0-9Xx]$";

    private final AppUserCenterMapper centerMapper;

    public AppRealNameService(AppUserCenterMapper centerMapper)
    {
        this.centerMapper = centerMapper;
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
        String materialRef = normalizeMaterialRefs(request.getMaterialRefs());

        String current = centerMapper.selectLatestRealNameStatus(userId);
        requireSubmittable(current);

        UserRealNameAuth record = new UserRealNameAuth();
        record.setUserId(userId);
        // 只落掩码：明文姓名与证件号不进入数据库
        record.setRealNameMask(AppMasks.maskRealName(realName));
        record.setIdNumberMask(AppMasks.maskIdNumber(idNumber));
        record.setMaterialRef(materialRef);
        record.setStatus(STATUS_PENDING);
        centerMapper.insertRealName(record);
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

    /**
     * 材料引用规范化：1–3 项，去空、去重、限长。
     * 单值列 user_real_name_auth.material_ref 与 A4 一致按「零或单元素」语义存储，
     * 因此多项时取首项（不解析分隔符拼接，避免 A4 侧按分隔符误拆）。
     */
    static String normalizeMaterialRefs(List<String> refs)
    {
        if (refs == null || refs.isEmpty())
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "material required");
        }
        String first = null;
        int count = 0;
        for (String ref : refs)
        {
            if (ref == null || ref.isBlank())
            {
                continue;
            }
            String value = ref.trim();
            if (value.length() > MATERIAL_REF_MAX_LENGTH)
            {
                throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "material ref too long");
            }
            if (first == null)
            {
                first = value;
            }
            count++;
            if (count > MATERIAL_REFS_MAX)
            {
                throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "too many material refs");
            }
        }
        if (first == null)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "material required");
        }
        return first;
    }
}
