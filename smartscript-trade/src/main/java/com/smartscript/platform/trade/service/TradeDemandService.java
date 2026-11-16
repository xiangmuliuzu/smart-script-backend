package com.smartscript.platform.trade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.file.FileNameLengthLimitExceededException;
import com.ruoyi.common.exception.file.FileSizeLimitExceededException;
import com.ruoyi.common.exception.file.InvalidExtensionException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandExtendApply;
import com.smartscript.platform.trade.domain.SysDemandStage;
import com.smartscript.platform.trade.domain.SysDemandSubmission;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.mapper.SysDemandExtendApplyMapper;
import com.smartscript.platform.trade.mapper.SysDemandMapper;
import com.smartscript.platform.trade.mapper.SysDemandStageMapper;
import com.smartscript.platform.trade.mapper.SysDemandSubmissionMapper;
import com.smartscript.platform.review.util.ReviewContentText;

/**
 * C module: demand project (task marketplace) service.
 */
@Service
public class TradeDemandService
{
    private static final Logger log = LoggerFactory.getLogger(TradeDemandService.class);

    public static final String DEMAND_STATUS_OPEN = "open";
    public static final String DEMAND_STATUS_CLOSED = "closed";
    public static final String DEMAND_STATUS_SELECTED = "selected";
    /** 接单制：已被抢单、进行中 */
    public static final String DEMAND_STATUS_IN_PROGRESS = "in_progress";
    /** 接单制：全部轮次验收通过、已完成 */
    public static final String DEMAND_STATUS_COMPLETED = "completed";
    /** 逐轮制：活动轮到点未通过，整单失败终止 */
    public static final String DEMAND_STATUS_FAILED = "failed";
    /** 定时扫描阈值：发布者超过该小时数仍未处理的延时申请 → 系统自动驳回 */
    private static final int EXTEND_AUTO_REJECT_HOURS = 24;
    /** 系统自动处置写入轮次 update_by 的标识，与真人操作区分 */
    private static final String OPERATOR_SYSTEM = "system";
    /** 接单制：未传时限时的默认完成时限（168 小时 = 7 天），仅用于无轮次的历史兼容单 */
    private static final int DEFAULT_DURATION_HOURS = 168;

    /**
     * 托管与押金资金状态常量已随需求域资金链路退役（sys_demand 的历史资金列仅作留档读取）。
     * 任务发布权收归管理端后，平台在站外与需求方结算，广场可接口径只看 status=open。
     */

    /** 任务类型：只征集剧本 / 剧本+分镜都要 / 提供剧本做分镜 */
    public static final String TASK_TYPE_SCRIPT = "script";
    public static final String TASK_TYPE_SCRIPT_STORYBOARD = "script_storyboard";
    public static final String TASK_TYPE_STORYBOARD = "storyboard";

    public static final String SUBMISSION_STATUS_SUBMITTED = "submitted";
    public static final String SUBMISSION_STATUS_SHORTLISTED = "shortlisted";
    public static final String SUBMISSION_STATUS_ACCEPTED = "accepted";
    public static final String SUBMISSION_STATUS_REJECTED = "rejected";

    @Autowired
    private SysDemandMapper demandMapper;

    @Autowired
    private SysDemandSubmissionMapper submissionMapper;

    @Autowired
    private SysDemandStageMapper stageMapper;

    @Autowired
    private SysDemandExtendApplyMapper extendApplyMapper;

    @Autowired
    private TradeInquiryService inquiryService;

    @Autowired
    private TradeNotifyService notifyService;

    /** 交付物 AI 审核（提交后、人工验收前的自动前置门） */
    @Autowired
    private DemandStageAiReviewService stageAiReviewService;

    /** 逐轮放款：本轮验收通过后生成该轮待结算单（与验收同事务） */
    @Autowired
    private TradeFinanceService financeService;

    public List<SysDemand> selectDemandList(SysDemand demand)
    {
        List<SysDemand> list = demandMapper.selectDemandList(demand);
        for (SysDemand d : list)
        {
            refreshRoundState(d);
        }
        return list;
    }

    /**
     * APP 任务广场真分页：仅对当前页行做惰性轮次刷新（避免全表刷），
     * 分页/排序/筛选由传入的 query 字段驱动。
     */
    public List<SysDemand> selectDemandPage(SysDemand demand)
    {
        List<SysDemand> list = demandMapper.selectDemandPage(demand);
        for (SysDemand d : list)
        {
            refreshRoundState(d);
        }
        return list;
    }

    /** 与 selectDemandPage 成对的独立总数（同 where）。 */
    public long countDemandList(SysDemand demand)
    {
        return demandMapper.countDemandList(demand);
    }

    /** Hero 轻量统计（广场口径）：可接单数 / 奖金池(元) / 今日新增。 */
    public Map<String, Object> selectDemandSquareStats()
    {
        return demandMapper.selectDemandSquareStats();
    }

    /** 题材分类数量角标：公开可接单口径（status=open），与广场列表一致。 */
    public List<Map<String, Object>> selectGenreCounts()
    {
        return demandMapper.selectGenreCounts();
    }

    /** 任务类型数量角标：与 selectGenreCounts 同口径。 */
    public List<Map<String, Object>> selectTypeCounts()
    {
        return demandMapper.selectTypeCounts();
    }

    /** 发布分镜任务时选择源剧本：本人未删除作品列表（源剧本仅作参考，不排除已上架作品）。 */
    public List<Map<String, Object>> selectSourceWorks(Long authorId)
    {
        return demandMapper.selectSourceWorks(authorId);
    }

    /** 源剧本上传大小上限：与容器 max-file-size 一致（10MB）。 */
    private static final long MAX_SOURCE_SCRIPT_BYTES = 10 * 1024 * 1024L;

    /** 源剧本文件扩展名白名单（与内容域剧本上传同口径）。 */
    private static final String[] SOURCE_SCRIPT_EXTENSIONS = { "txt", "doc", "docx", "pdf" };

    /**
     * 上传分镜任务的源剧本文件。发布者是买方角色、可能无创作者资格，走不了 content 域上传口，
     * 故在交易域开口；复用若依存储实现（同一上传目录与扩展名校验）。
     * url 下发相对路径（/profile/upload/...），由客户端按自身 baseUrl 解析，
     * 避免把上传者机器地址写进共享库（与内容域上传同一约定）。
     */
    public Map<String, Object> uploadSourceScript(MultipartFile file)
    {
        if (file == null || file.isEmpty()) throw new RuntimeException("文件不能为空");
        if (file.getSize() > MAX_SOURCE_SCRIPT_BYTES) throw new RuntimeException("剧本文件不能超过10MB");
        final String storedPath;
        try
        {
            storedPath = FileUploadUtils.upload(RuoYiConfig.getUploadPath(), file, SOURCE_SCRIPT_EXTENSIONS);
        }
        catch (InvalidExtensionException | FileSizeLimitExceededException | FileNameLengthLimitExceededException e)
        {
            throw new RuntimeException("仅支持 txt/doc/docx/pdf 格式的剧本文件");
        }
        catch (IOException e)
        {
            log.warn("源剧本上传失败: {}", e.getClass().getSimpleName());
            throw new RuntimeException("上传失败，请稍后重试");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", storedPath);
        result.put("fileName", file.getOriginalFilename());
        return result;
    }

    public SysDemand selectDemandById(Long demandId)
    {
        SysDemand demand = demandMapper.selectDemandById(demandId);
        refreshRoundState(demand);
        return demand;
    }

    /**
     * 读取惰性刷新（逐轮）：in_progress 单取当前活动轮，
     * 若已过本轮 due_at 且未通过 → 整单失败终止（资金已在站外结算，仅落失败态与原因）；
     * 否则把本轮 due_at/逾期镜像到 sys_demand.due_at/is_overdue 供卡片/列表读取。
     * 无轮次的历史兼容单退回旧的整单逾期逻辑。
     */
    private void refreshRoundState(SysDemand demand)
    {
        if (demand == null) return;
        if (!DEMAND_STATUS_IN_PROGRESS.equals(demand.getStatus())) return;

        SysDemandStage active = stageMapper.selectActiveStage(demand.getDemandId());
        if (active == null)
        {
            // 无活动轮：历史兼容，按整单 due_at 判逾期
            refreshLegacyOverdue(demand);
            return;
        }
        Date now = new Date();
        // 交付后（ai_reviewing AI审核中 / submitted 待人工验收）暂停计时：审核耗时不计入创作者时限，
        // 截止顺延分段补回（AI完成时补 AI 耗时，人工驳回时补人工耗时）
        // 待处理的延时申请同样暂停超时判定：防止申请还在排队就被判失败终止；
        // 但暂停时长不补回（同意只加申请时长，驳回保持原截止），避免“无论结果都白赚等待时长”的反向激励
        boolean paused = SysDemandStage.STAGE_STATUS_SUBMITTED.equals(active.getStatus())
                || SysDemandStage.STAGE_STATUS_AI_REVIEWING.equals(active.getStatus())
                || SysDemandStage.EXTEND_PENDING.equals(active.getExtendStatus());
        boolean overdue = !paused && active.getDueAt() != null && now.after(active.getDueAt());
        if (overdue)
        {
            terminateRound(demand, active, now);
            return;
        }
        // 未逾期：镜像本轮截止/逾期到 sys_demand
        Integer overdueFlag = 0;
        demandMapper.mirrorRoundState(demand.getDemandId(), active.getDueAt(), overdueFlag);
        demand.setDueAt(active.getDueAt());
        demand.setOverdue(overdueFlag);
    }

    /**
     * 超时失败终止（读路径惰性判定与定时扫描共用，判定与处置口径必须唯一）：
     * 先以 CAS 落失败态（仅 in_progress 生效），影响 0 行说明已被并发路径终止，
     * 直接返回 false 不重复通知；赢家再标记本轮逾期并通知双方。
     *
     * @return true=本次调用完成了终止，false=已被并发路径终止（未做任何写入）
     */
    private boolean terminateRound(SysDemand demand, SysDemandStage active, Date now)
    {
        String reason = "第" + active.getStageOrder() + "轮验收超时未通过，任务失败终止";
        int changed = demandMapper.failDemandIfInProgress(demand.getDemandId(), DEMAND_STATUS_FAILED, reason);
        if (changed == 0)
        {
            return false;
        }
        stageMapper.markStageOverdue(active.getStageId());
        demand.setStatus(DEMAND_STATUS_FAILED);
        demand.setFailReason(reason);
        demand.setFailAt(now);
        demand.setOverdue(1);
        demand.setDueAt(active.getDueAt());
        notifyService.notifyDemandRoundFailed(demand.getDemandId(), demand.getTitle(),
                active.getStageOrder(), reason, demand.getClientId(), demand.getAcceptedCreatorId());
        return true;
    }

    /**
     * 定时扫描入口（供 tradeDemandTask.expireOverdueRounds 调用）：
     * ① 先把发布者超过 24 小时未处理的延时申请自动驳回——pending 会暂停超时判定，不先驳回则到期任务永远判不掉；
     * ② 再终止全部已过截止且仍在计时的活动轮。
     * 单条异常仅记日志不中断整批（下一轮扫描仍会重试）；终止自身是 CAS、通知按 requestId 幂等，重复执行安全。
     * 无事务上下文：逐条写入各自提交，与读路径惰性判定的现有行为保持一致。
     *
     * @return 本次生效的处置条数（自动驳回数 + 超时终止数）
     */
    public int expireOverdueRounds()
    {
        int rejected = autoRejectStaleExtendApplies();
        int failed = terminateOverdueRounds();
        return rejected + failed;
    }

    /** 跨全库扫描已到期的活动轮，逐条复用读路径同一个终止方法。 */
    private int terminateOverdueRounds()
    {
        List<SysDemandStage> overdue = stageMapper.selectOverdueActiveStages();
        int count = 0;
        for (SysDemandStage active : overdue)
        {
            try
            {
                SysDemand demand = demandMapper.selectDemandById(active.getDemandId());
                if (demand == null || !DEMAND_STATUS_IN_PROGRESS.equals(demand.getStatus()))
                {
                    continue;
                }
                if (terminateRound(demand, active, new Date()))
                {
                    count++;
                }
            }
            catch (Exception e)
            {
                log.warn("定时超时终止失败 demandId={} stageId={}: {}",
                        active.getDemandId(), active.getStageId(), e.getMessage());
            }
        }
        return count;
    }

    /**
     * 发布者长期不处理延时申请 → 系统自动驳回：只落决定、不挪截止（与人工驳回同口径，
     * 暂停期间的时长不补回）；记录行结果仍为已拒绝，处理人留空表示系统处置。
     * 驳回后该轮暂停条件解除，紧随的超时扫描会按原有口径判定（若已过截止则立即终止）。
     */
    private int autoRejectStaleExtendApplies()
    {
        List<SysDemandExtendApply> stale = extendApplyMapper.selectStalePendingApplies(EXTEND_AUTO_REJECT_HOURS);
        int count = 0;
        for (SysDemandExtendApply apply : stale)
        {
            try
            {
                int changed = extendApplyMapper.decide(apply.getApplyId(),
                        SysDemandExtendApply.STATUS_REJECTED, null, null);
                if (changed == 0)
                {
                    // 发布者恰好同时处理了，不抢他的决定
                    continue;
                }
                stageMapper.decideExtend(apply.getStageId(), SysDemandStage.EXTEND_REJECTED,
                        null, OPERATOR_SYSTEM);
                SysDemand demand = demandMapper.selectDemandById(apply.getDemandId());
                notifyService.notifyExtendAutoRejected(apply.getApplyId(), apply.getDemandId(),
                        demand == null ? null : demand.getTitle(), apply.getStageOrder(),
                        apply.getDays(), apply.getHours(), apply.getCreatorId());
                count++;
            }
            catch (Exception e)
            {
                log.warn("延时申请自动驳回失败 applyId={}: {}", apply.getApplyId(), e.getMessage());
            }
        }
        return count;
    }

    /** 历史兼容：无轮次单按整单 due_at 惰性逾期。 */
    private void refreshLegacyOverdue(SysDemand demand)
    {
        if (demand.getDueAt() != null
                && (demand.getOverdue() == null || demand.getOverdue() == 0)
                && new Date().after(demand.getDueAt()))
        {
            demandMapper.markOverdue(demand.getDemandId());
            demand.setOverdue(1);
        }
    }

    public List<SysDemandSubmission> selectSubmissionsByDemandId(Long demandId)
    {
        return submissionMapper.selectSubmissionsByDemandId(demandId);
    }

    public List<SysDemandSubmission> selectMySubmissions(Long submitterId)
    {
        return submissionMapper.selectBySubmitterId(submitterId);
    }

    /**
     * 发布征集令（含验收轮次）。
     */
    @Transactional
    public int insertDemand(SysDemand demand, List<SysDemandStage> stages)
    {
        // 总集数必填；未显式配置轮次时也固定生成最后一轮验收
        if (demand.getTotalEpisodes() == null || demand.getTotalEpisodes() <= 0)
        {
            throw new RuntimeException("请填写总集数");
        }
        if (stages == null || stages.isEmpty())
        {
            stages = buildFinalAcceptanceOnly(demand, null);
        }
        validateStages(demand, stages);

        // 接单制：完成时限。未传时给默认值，传入时必须 >0。
        if (demand.getDurationHours() == null)
        {
            demand.setDurationHours(DEFAULT_DURATION_HOURS);
        }
        else if (demand.getDurationHours() <= 0)
        {
            throw new RuntimeException("完成时限必须大于0小时");
        }

        int rows = insertDemand(demand);

        // 批量插入轮次
        if (stages != null && !stages.isEmpty() && demand.getDemandId() != null)
        {
            for (SysDemandStage s : stages)
            {
                s.setDemandId(demand.getDemandId());
                s.setStatus(SysDemandStage.STAGE_STATUS_PENDING);
                s.setReleaseFlag(0);
                s.setCreateBy(demand.getCreateBy());
                normalizeRoundDuration(s);
            }
            stageMapper.insertBatch(stages);
        }
        return rows;
    }

    /**
     * 创作者抢单：先到先得，原子锁定。前置：status=open 且非本人发布。
     * 成功后激活首轮计时（写入 started_at/due_at），不再有押金入账环节。
     */
    @Transactional
    public SysDemand claimDemand(Long demandId, Long creatorId)
    {
        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("任务不存在");
        if (!DEMAND_STATUS_OPEN.equals(demand.getStatus()))
        {
            throw new RuntimeException("该任务已被接单或不可接单");
        }
        if (creatorId != null && creatorId.equals(demand.getClientId()))
        {
            throw new RuntimeException("不能接自己发布的任务");
        }

        Date now = new Date();

        // 逐轮：取首轮并计算其截止；无轮次则用整单 durationHours 兑底
        SysDemandStage round1 = firstStage(stageMapper.selectByDemandId(demandId));
        Date dueAt = (round1 != null) ? computeRoundDueAt(now, round1)
                : plusHours(now, demand.getDurationHours() == null ? DEFAULT_DURATION_HOURS : demand.getDurationHours());

        int affected = demandMapper.claimDemand(demandId, creatorId, now, dueAt, String.valueOf(creatorId));
        if (affected == 0)
        {
            throw new RuntimeException("该任务已被接单，手慢了");
        }
        if (round1 != null)
        {
            stageMapper.activateStage(round1.getStageId(), now, dueAt, String.valueOf(creatorId));
        }
        notifyService.notifyDemandClaimed(demandId, demand.getTitle(), demand.getClientId(), null);
        return demandMapper.selectDemandById(demandId);
    }

    /**
     * 编辑任务（仅运营管理员入口调用）：更新主字段并重建验收轮次。
     *
     * <p>前置：任务存在、status=open（未被接单/未关闭）；提供 stages 则先校验后删旧插新重建。
     * 调用方是带 {@code trade:demand:edit} 权限的管理端接口，授权由权限位把关，
     * 服务层不再比对 client_id（发布权已收归平台，历史单的 client_id 可能是普通用户）。</p>
     */
    @Transactional
    public SysDemand updateDemand(SysDemand demand, List<SysDemandStage> stages, Long operatorId)
    {
        if (demand == null || demand.getDemandId() == null)
        {
            throw new RuntimeException("任务ID缺失");
        }
        SysDemand existing = demandMapper.selectDemandForUpdate(demand.getDemandId());
        if (existing == null) throw new RuntimeException("任务不存在");
        if (!DEMAND_STATUS_OPEN.equals(existing.getStatus()))
        {
            throw new RuntimeException("任务已被接单或已关闭，不可编辑");
        }

        // 基础字段校验（标题/截止/联系电话）
        validateDemandBasics(demand);
        demand.setClientId(existing.getClientId());
        // 未显式传类型时沿用原任务及其源剧本（兼容不带任务类型的旧客户端编辑）
        if (demand.getTaskType() == null)
        {
            demand.setTaskType(existing.getTaskType());
            demand.setSourceWorkId(existing.getSourceWorkId());
            demand.setSourceScriptUrl(existing.getSourceScriptUrl());
            demand.setSourceScriptName(existing.getSourceScriptName());
        }
        normalizeAndValidateTaskType(demand);
        // 总集数必填；未显式配置轮次时也固定生成最后一轮验收
        if (demand.getTotalEpisodes() == null || demand.getTotalEpisodes() <= 0)
        {
            throw new RuntimeException("请填写总集数");
        }
        if (stages == null || stages.isEmpty())
        {
            stages = buildFinalAcceptanceOnly(demand, existing);
        }
        validateStages(demand, stages);
        demand.setUpdateBy(String.valueOf(operatorId));
        demandMapper.updateDemand(demand);

        // 重建轮次：先删后插（任务仍为 open 未接单，旧轮必为 pending，无交付数据丢失风险）
        stageMapper.deleteByDemandId(demand.getDemandId());
        if (stages != null && !stages.isEmpty())
        {
            for (SysDemandStage s : stages)
            {
                s.setDemandId(demand.getDemandId());
                s.setStatus(SysDemandStage.STAGE_STATUS_PENDING);
                s.setReleaseFlag(0);
                s.setCreateBy(String.valueOf(operatorId));
                normalizeRoundDuration(s);
            }
            stageMapper.insertBatch(stages);
        }
        return demandMapper.selectDemandById(demand.getDemandId());
    }

    /** 建单/改单共用的基础字段校验（标题/截止日期/联系电话格式）。 */
    private static void validateDemandBasics(SysDemand demand)
    {
        if (demand.getTitle() == null || demand.getTitle().trim().isEmpty())
        {
            throw new RuntimeException("征集标题不能为空");
        }
        if (demand.getDeadline() != null && startOfDay(demand.getDeadline()).before(startOfDay(new Date())))
        {
            throw new RuntimeException("截止日期不能早于当前日期");
        }
        String contact = demand.getContactInfo() == null ? "" : demand.getContactInfo().trim();
        if (!contact.isEmpty() && contact.matches("^[\\d\\s-]+$")
                && !contact.matches("^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8}|(400|800)-?\\d{3}-?\\d{4})$"))
        {
            throw new RuntimeException("联系电话格式不正确");
        }
    }

    /**
     * 任务类型归一与校验（建单/改单共用）：
     * 缺省回落 script；storyboard 必须至少提供一种源剧本（作品或上传文件），
     * 选作品时只校验作品存在——发布权已收归平台，源剧本由运营按需求方授权代选，
     * 不再要求它属于 client_id 对应用户（新单 client_id 是运营账号）。
     * 非 storyboard 类型清空源剧本字段，防止改类型后残留。
     */
    private void normalizeAndValidateTaskType(SysDemand demand)
    {
        String type = demand.getTaskType() == null || demand.getTaskType().trim().isEmpty()
                ? TASK_TYPE_SCRIPT : demand.getTaskType().trim();
        if (!TASK_TYPE_SCRIPT.equals(type) && !TASK_TYPE_SCRIPT_STORYBOARD.equals(type)
                && !TASK_TYPE_STORYBOARD.equals(type))
        {
            throw new RuntimeException("任务类型不正确");
        }
        demand.setTaskType(type);
        String url = demand.getSourceScriptUrl() == null ? "" : demand.getSourceScriptUrl().trim();
        demand.setSourceScriptUrl(url.isEmpty() ? null : url);
        String name = demand.getSourceScriptName() == null ? "" : demand.getSourceScriptName().trim();
        demand.setSourceScriptName(name.isEmpty() ? null : name);
        if (!TASK_TYPE_STORYBOARD.equals(type))
        {
            demand.setSourceWorkId(null);
            demand.setSourceScriptUrl(null);
            demand.setSourceScriptName(null);
            return;
        }
        if (demand.getSourceWorkId() == null && demand.getSourceScriptUrl() == null)
        {
            throw new RuntimeException("提供剧本做分镜的任务，请选择源剧本作品或上传剧本文件");
        }
        if (demand.getSourceWorkId() != null && stageMapper.selectWorkBrief(demand.getSourceWorkId()) == null)
        {
            throw new RuntimeException("源剧本作品不存在");
        }
    }

    /**
     * 轮次时限归一：库内 duration_days / duration_hours 均为 NOT NULL DEFAULT 0，而
     * insertBatch 是显式列赋值（不走列默认值），调用方只填天数时会把 null 直接写入，
     * 报 “Column 'duration_hours' cannot be null” 使发布/编辑接口 500。
     * 校验口径本就把 null 当 0 看（见 validateStages 对 days+hours 的判断），故落库前统一归一。
     */
    private static void normalizeRoundDuration(SysDemandStage s)
    {
        if (s.getDurationDays() == null)
        {
            s.setDurationDays(0);
        }
        if (s.getDurationHours() == null)
        {
            s.setDurationHours(0);
        }
    }

    /**
     * 建单/改单共用的验收轮次校验（stages 非空时）。
     *
     * <p>固定存在最后一轮验收：末轮集数必须等于总集数，其金额=预算-其余轮之和（可为 0，不可单独改动），
     * 因此填写的中间轮金额之和允许小于预算，但全部轮次之和必须等于预算（逐轮全额放款口径不变）。
     * 其余校验：集数严格递增且不超总集数、中间轮金额>0、每轮时限>0。</p>
     */
    private static void validateStages(SysDemand demand, List<SysDemandStage> stages)
    {
        if (demand.getTotalEpisodes() == null || demand.getTotalEpisodes() <= 0)
        {
            throw new RuntimeException("设置验收轮次时总集数必填");
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        int prevEpisode = 0;
        int last = stages.size() - 1;
        for (int i = 0; i < stages.size(); i++)
        {
            SysDemandStage s = stages.get(i);
            if (s.getEpisodeTo() == null || s.getEpisodeTo() <= prevEpisode)
            {
                throw new RuntimeException("验收集数必须严格递增且大于0");
            }
            if (s.getEpisodeTo() > demand.getTotalEpisodes())
            {
                throw new RuntimeException("验收集数不能超过总集数");
            }
            if (i == last)
            {
                // 最后一轮验收：集数=总集数，金额=剩余（允许为 0，不允许为负）
                if (!s.getEpisodeTo().equals(demand.getTotalEpisodes()))
                {
                    throw new RuntimeException("固定有最后一轮验收，其集数必须等于总集数");
                }
                if (s.getAmount() == null || s.getAmount().compareTo(BigDecimal.ZERO) < 0)
                {
                    throw new RuntimeException("最后一轮金额自动为预算减去各轮之和，不能为负");
                }
            }
            else if (s.getAmount() == null || s.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new RuntimeException("每轮金额必须大于0");
            }
            int days = s.getDurationDays() == null ? 0 : s.getDurationDays();
            int hours = s.getDurationHours() == null ? 0 : s.getDurationHours();
            if (days < 0 || hours < 0 || (days + hours) <= 0)
            {
                throw new RuntimeException("每轮验收时限（小时）必须大于0");
            }
            prevEpisode = s.getEpisodeTo();
            totalAmount = totalAmount.add(s.getAmount());
        }
        if (demand.getBudget() == null || totalAmount.compareTo(demand.getBudget()) != 0)
        {
            throw new RuntimeException("各填写轮金额之和不能超过预算，最后一轮金额须等于预算减去其余轮次之和");
        }
    }

    /**
     * 未配置中间轮次时的最后一轮验收：集数=总集数、金额=预算余额（即全额）、时限按小时兜底。
     * 编辑场景下预算/时限可能未传，回落到原任务值。
     */
    private static List<SysDemandStage> buildFinalAcceptanceOnly(SysDemand demand, SysDemand fallback)
    {
        BigDecimal budget = demand.getBudget() != null
                ? demand.getBudget()
                : (fallback == null ? null : fallback.getBudget());
        int hours;
        if (demand.getDurationHours() != null && demand.getDurationHours() > 0)
        {
            hours = demand.getDurationHours();
        }
        else if (fallback != null && fallback.getDurationHours() != null && fallback.getDurationHours() > 0)
        {
            hours = fallback.getDurationHours();
        }
        else
        {
            hours = DEFAULT_DURATION_HOURS;
        }
        SysDemandStage stage = new SysDemandStage();
        stage.setStageOrder(1);
        stage.setEpisodeTo(demand.getTotalEpisodes());
        stage.setAmount(budget == null ? BigDecimal.ZERO : budget);
        stage.setDurationDays(0);
        stage.setDurationHours(hours);
        List<SysDemandStage> stages = new ArrayList<>();
        stages.add(stage);
        return stages;
    }

    /** 取 stage_order 最小的首轮；无则 null。 */
    private static SysDemandStage firstStage(List<SysDemandStage> stages)
    {
        if (stages == null || stages.isEmpty()) return null;
        SysDemandStage first = null;
        for (SysDemandStage s : stages)
        {
            if (first == null || s.getStageOrder() < first.getStageOrder()) first = s;
        }
        return first;
    }

    /** 本轮截止 = 起点 + days 天 + hours 小时。 */
    private static Date computeRoundDueAt(Date start, SysDemandStage stage)
    {
        int days = stage.getDurationDays() == null ? 0 : stage.getDurationDays();
        int hours = stage.getDurationHours() == null ? 0 : stage.getDurationHours();
        Calendar cal = Calendar.getInstance();
        cal.setTime(start);
        cal.add(Calendar.DAY_OF_MONTH, days);
        cal.add(Calendar.HOUR_OF_DAY, hours);
        return cal.getTime();
    }

    private static Date plusHours(Date start, int hours)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(start);
        cal.add(Calendar.HOUR_OF_DAY, hours);
        return cal.getTime();
    }

    /**
     * 创作者提交本轮交付物。
     * script/script_storyboard：必须携带本人作品（work_id），已上架售卖（on_shelf）作品不得用于交付；
     * 提交后本轮先进入 AI 审核（ai_reviewing）：计时暂停，后台 AI 打分 >=60 自动转人工验收(submitted)，
     * <60 自动打回(rejected)，AI 审核本身不需要人工介入。
     * storyboard（提供剧本做分镜）：分镜无剧本正文可审，作品可选（未选作品时须给文件链接或交付说明），
     * 跳过 AI 审核直接进入人工验收(submitted)，计时暂停口径与 AI 审核一致。
     */
    @Transactional
    public SysDemandStage deliverStage(Long demandId, Long stageId, Long creatorId,
                                       String deliverableDesc, String deliverableUrl, Long workId)
    {
        SysDemandStage stage = stageMapper.selectByStageId(stageId);
        if (stage == null) throw new RuntimeException("轮次不存在");
        if (!demandId.equals(stage.getDemandId())) throw new RuntimeException("轮次与任务不匹配");

        // 校验：只有抢单成功且处于进行中的创作者才能交付
        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("任务不存在");
        if (!DEMAND_STATUS_IN_PROGRESS.equals(demand.getStatus()))
        {
            throw new RuntimeException("任务尚未被接单或不在进行中");
        }
        if (demand.getAcceptedCreatorId() == null || !demand.getAcceptedCreatorId().equals(creatorId))
        {
            throw new RuntimeException("只有接单创作者可以提交交付物");
        }

        // 状态校验
        if (!SysDemandStage.STAGE_STATUS_PENDING.equals(stage.getStatus())
                && !SysDemandStage.STAGE_STATUS_REJECTED.equals(stage.getStatus()))
        {
            throw new RuntimeException("当前轮次状态不可提交");
        }

        // 只允许交付当前生效轮次：防止越轮交付到尚未激活或已被驳回终止的轮次
        SysDemandStage active = stageMapper.selectActiveStage(demandId);
        if (active == null || !active.getStageId().equals(stageId))
        {
            throw new RuntimeException("只能交付当前进行中的轮次");
        }

        boolean storyboardOnly = TASK_TYPE_STORYBOARD.equals(demand.getTaskType());
        if (storyboardOnly)
        {
            deliverableDesc = deliverableDesc == null || deliverableDesc.trim().isEmpty() ? null : deliverableDesc.trim();
            deliverableUrl = deliverableUrl == null || deliverableUrl.trim().isEmpty() ? null : deliverableUrl.trim();
        }
        // 交付作品校验：剧本类必选；分镜类可选，未选作品时须给分镜文件链接或交付说明
        if (workId == null)
        {
            if (!storyboardOnly) throw new RuntimeException("请选择要交付的作品");
            if (deliverableUrl == null && deliverableDesc == null)
            {
                throw new RuntimeException("请上传分镜文件链接或填写交付说明");
            }
        }
        else
        {
            Map<String, Object> work = stageMapper.selectWorkBrief(workId);
            if (work == null) throw new RuntimeException("交付作品不存在");
            Object authorId = work.get("authorId");
            if (authorId == null || !creatorId.equals(((Number) authorId).longValue()))
            {
                throw new RuntimeException("只能交付自己创作的作品");
            }
            if ("on_shelf".equals(String.valueOf(work.get("status"))))
            {
                throw new RuntimeException("作品已上架售卖，不能用于任务交付；请先下架后再提交");
            }
        }

        if (storyboardOnly)
        {
            // 分镜交付跳过 AI 剧本审核，直接待人工验收（submitted_at 由 updateDeliverable 统一写入）
            int changed = stageMapper.updateDeliverable(stageId, SysDemandStage.STAGE_STATUS_SUBMITTED,
                    deliverableDesc, deliverableUrl, workId, String.valueOf(creatorId));
            if (changed == 0) throw new RuntimeException("本轮状态已被变更，请刷新后重新提交");
        }
        else
        {
            int changed = stageMapper.updateDeliverable(stageId, SysDemandStage.STAGE_STATUS_AI_REVIEWING,
                    deliverableDesc, deliverableUrl, workId, String.valueOf(creatorId));
            if (changed == 0) throw new RuntimeException("本轮状态已被变更，请刷新后重新提交");
            // 交付事务提交后再触发后台 AI 审核，避免异步线程读到旧状态
            triggerAiReviewAfterCommit(stageId);
        }
        SysDemandStage delivered = stageMapper.selectByStageId(stageId);
        if (storyboardOnly)
        {
            // 分镜直达人工验收：与 AI 审核通过转人工同口径通知发布者
            notifyService.notifyStageReadyForReview(demand, delivered);
        }
        return delivered;
    }

    /** 注册事务提交后回调触发 AI 审核（无事务时直接触发）。 */
    private void triggerAiReviewAfterCommit(Long stageId)
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    stageAiReviewService.trigger(stageId);
                }
            });
        }
        else
        {
            stageAiReviewService.trigger(stageId);
        }
    }

    /**
     * 审核轮次（需求方动作，现由平台运营在管理端执行）。
     *
     * <p>调用方须已通过 trade:demand:review 权限校验，服务层不再比对 client_id：
     * 需求方已不再是平台账号，历史 client_id 只用于留档与通知。operatorId 仅作为 update_by 落审计。</p>
     */
    @Transactional
    public SysDemandStage reviewStage(Long demandId, Long stageId, Long operatorId,
                                      boolean approved, String rejectReason)
    {
        SysDemandStage stage = stageMapper.selectByStageId(stageId);
        if (stage == null) throw new RuntimeException("轮次不存在");
        if (!demandId.equals(stage.getDemandId())) throw new RuntimeException("轮次与任务不匹配");
        if (!SysDemandStage.STAGE_STATUS_SUBMITTED.equals(stage.getStatus()))
        {
            throw new RuntimeException(SysDemandStage.STAGE_STATUS_AI_REVIEWING.equals(stage.getStatus())
                    ? "AI审核中，请等待审核结果" : "当前轮次非待审核状态");
        }

        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null)
        {
            throw new RuntimeException("任务不存在");
        }

        String newStatus = approved ? SysDemandStage.STAGE_STATUS_APPROVED : SysDemandStage.STAGE_STATUS_REJECTED;
        int releaseFlag = approved ? 1 : 0;
        int changed = stageMapper.updateReview(stageId, newStatus, approved ? null : rejectReason,
                releaseFlag, String.valueOf(operatorId));
        if (changed == 0)
        {
            // CAS 未命中：本轮已被并发请求（双击/双端同时提交）处理完，绝不能再做放款等副作用
            throw new RuntimeException("本轮已被处理，请刷新后重试");
        }

        // 逐轮：通过则本轮放款记账，并激活下一轮或整单完成
        if (approved)
        {
            Date now = new Date();
            // 本轮款项 → 待结算单（作者确认后入可提现）；零额轮次（末轮预算余额为 0）不生成
            if (stage.getAmount() != null && stage.getAmount().compareTo(BigDecimal.ZERO) > 0)
            {
                Map<String, Object> settle = financeService.createPendingStageSettle(demand, stage);
                notifyService.notifyStageSettleCreated(demand.getDemandId(), demand.getTitle(),
                        stage.getStageOrder(), stage.getAmount(),
                        TradeFinanceService.toDecimal(settle.get("sellerAmount")),
                        demand.getAcceptedCreatorId());
            }
            SysDemandStage next = stageMapper.selectNextPending(demandId, stage.getStageOrder());
            if (next != null)
            {
                // 顺序起算：下一轮起点=本轮通过时刻（本轮剩余时间不结转）
                Date nextDue = computeRoundDueAt(now, next);
                stageMapper.activateStage(next.getStageId(), now, nextDue, String.valueOf(operatorId));
                demandMapper.mirrorRoundState(demandId, nextDue, 0);
                // 下一轮激活→通知创作者新一轮已开始（携本轮金额与新截止时间）
                notifyService.notifyNextRoundActivated(demand, next, nextDue);
            }
            else
            {
                // 无下一轮 → 全部通过 → 整单完成（资金已在站外结算，此处只推进任务状态）
                if (!DEMAND_STATUS_COMPLETED.equals(demand.getStatus()))
                {
                    demandMapper.updateStatus(demandId, DEMAND_STATUS_COMPLETED, String.valueOf(operatorId));
                    // 任务完成→通知双方（demandId 单键幂等，上游 !COMPLETED 已保证仅进入一次）
                    notifyService.notifyDemandCompleted(demand);
                }
            }
        }
        else
        {
            // 驳回恢复计时：待审核停留时长（AI审核完成→人工审核）不计入时限，截止同步往后顺延
            Date now = new Date();
            // AI 审核已通过的本轮，暂停起点取 AI 完成时刻（提交→AI完成 的耗时已在 AI 完成时补回）
            Date pauseStart = stage.getAiReviewedAt() != null ? stage.getAiReviewedAt() : stage.getSubmittedAt();
            // 兜底：无法计算新截止时，保持原 dueAt（仅用于通知文案展示）
            Date newDue = stage.getDueAt();
            if (pauseStart != null && stage.getDueAt() != null)
            {
                long pausedMillis = Math.max(0, now.getTime() - pauseStart.getTime());
                Calendar cal = Calendar.getInstance();
                cal.setTime(stage.getDueAt());
                cal.add(Calendar.MILLISECOND, (int) pausedMillis);
                newDue = cal.getTime();
                stageMapper.pushDue(stageId, newDue, String.valueOf(operatorId));
                demandMapper.mirrorRoundState(demandId, newDue, 0);
            }
            // 人工驳回→通知创作者重交（携原因与新截止）
            notifyService.notifyStageRejectedByBuyer(demand, stage, rejectReason, newDue, now.getTime());
        }

        return stageMapper.selectByStageId(stageId);
    }

    /**
     * 创作者对活动轮发起延时申请：每次申请在 sys_demand_extend_apply 新增一条记录，
     * 既往申请（含已通过/已驳回）全部保留；轮次行仅存最近一次申请快照。
     * 同轮同时只允许一条 pending 申请（行锁串行化）；仅尚在计时的轮次可申请。
     */
    @Transactional
    public SysDemandStage requestExtend(Long demandId, Long stageId, Long creatorId,
                                        int days, int hours, String reason)
    {
        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("任务不存在");
        if (!DEMAND_STATUS_IN_PROGRESS.equals(demand.getStatus()))
        {
            throw new RuntimeException("任务不在进行中，无法申请延时");
        }
        if (demand.getAcceptedCreatorId() == null || !demand.getAcceptedCreatorId().equals(creatorId))
        {
            throw new RuntimeException("只有接单创作者可以申请延时");
        }
        SysDemandStage active = requireActiveStage(demandId, stageId);
        if (days < 0 || hours < 0 || days + hours <= 0)
        {
            throw new RuntimeException("延时时长（小时）必须大于0");
        }
        // 只在尚在计时的轮次上申请：已提交/AI审核中计时已暂停，此时申请延时没有意义
        requireDeliverableStage(active);
        // 锁住轮次行后再判重，避免并发同时通过检查产生两条 pending
        extendApplyMapper.lockStage(stageId);
        if (extendApplyMapper.selectPendingByStageId(stageId) != null)
        {
            throw new RuntimeException("已有待处理的延时申请，请等待平台处理");
        }
        SysDemandExtendApply apply = new SysDemandExtendApply();
        apply.setDemandId(demandId);
        apply.setStageId(stageId);
        apply.setCreatorId(creatorId);
        apply.setDays(days);
        apply.setHours(hours);
        apply.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        apply.setStatus(SysDemandExtendApply.STATUS_PENDING);
        apply.setApplyType(SysDemandExtendApply.TYPE_REQUEST);
        extendApplyMapper.insert(apply);
        // 轮次行快照：供列表快速展示待处理状态与审核入口
        stageMapper.requestExtend(stageId, days, hours,
                apply.getReason(), String.valueOf(creatorId));
        notifyService.notifyExtendRequested(apply.getApplyId(), demandId, demand.getTitle(),
                active.getStageOrder(), days, hours, apply.getReason(), demand.getClientId());
        return stageMapper.selectByStageId(stageId);
    }

    /**
     * 处理延时申请（需求方动作，现由平台运营在管理端执行）：只处理当条 pending 记录
     * （条件更新防并发重复处理），同意则截止同步顺延并回写该条记录的 due_after，
     * 不同意仅落决定；处理与驳回记录均永久保留。
     *
     * <p>授权来自管理端 trade:demand:edit 权限，服务层不再比对 client_id。</p>
     */
    @Transactional
    public SysDemandStage decideExtend(Long demandId, Long stageId, Long operatorId, boolean approved)
    {
        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("任务不存在");
        if (!DEMAND_STATUS_IN_PROGRESS.equals(demand.getStatus()))
        {
            throw new RuntimeException("任务不在进行中，无法处理延时申请");
        }
        SysDemandStage stage = stageMapper.selectByStageId(stageId);
        if (stage == null) throw new RuntimeException("轮次不存在");
        if (!demandId.equals(stage.getDemandId())) throw new RuntimeException("轮次与任务不匹配");
        extendApplyMapper.lockStage(stageId);
        SysDemandExtendApply pending = extendApplyMapper.selectPendingByStageId(stageId);
        if (pending == null)
        {
            throw new RuntimeException("无待处理的延时申请");
        }
        Date newDue = null;
        if (approved)
        {
            if (stage.getDueAt() == null) throw new RuntimeException("该轮尚未激活，无截止时间可顺延");
            newDue = plusDuration(stage.getDueAt(),
                    pending.getDays() == null ? 0 : pending.getDays(),
                    pending.getHours() == null ? 0 : pending.getHours());
        }
        int changed = extendApplyMapper.decide(pending.getApplyId(),
                approved ? SysDemandExtendApply.STATUS_APPROVED : SysDemandExtendApply.STATUS_REJECTED,
                operatorId, newDue);
        if (changed == 0)
        {
            throw new RuntimeException("该延时申请已被处理");
        }
        stageMapper.decideExtend(stageId,
                approved ? SysDemandStage.EXTEND_APPROVED : SysDemandStage.EXTEND_REJECTED,
                newDue, String.valueOf(operatorId));
        if (newDue != null)
        {
            demandMapper.mirrorRoundState(demandId, newDue, 0);
        }
        // 驳回不补回暂停期间的时长（见 refreshRoundState 的计时口径）
        notifyService.notifyExtendDecided(pending.getApplyId(), demandId, demand.getTitle(),
                stage.getStageOrder(), approved, pending.getDays(), pending.getHours(),
                newDue, demand.getAcceptedCreatorId());
        return stageMapper.selectByStageId(stageId);
    }

    /**
     * 某轮延时记录（创作者申请延时 + 发布者自主延时）：每次动作一行，通过/驳回均保留，按发生时间倒序。
     * 仅任务双方（发布者/接单创作者）可查。
     */
    public List<SysDemandExtendApply> selectExtendHistory(Long demandId, Long stageId, Long userId)
    {
        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("任务不存在");
        boolean isBuyer = userId != null && userId.equals(demand.getClientId());
        boolean isCreator = userId != null && userId.equals(demand.getAcceptedCreatorId());
        if (!isBuyer && !isCreator)
        {
            throw new RuntimeException("无权查看该任务的延时记录");
        }
        SysDemandStage stage = stageMapper.selectByStageId(stageId);
        if (stage == null) throw new RuntimeException("轮次不存在");
        if (!demandId.equals(stage.getDemandId())) throw new RuntimeException("轮次与任务不匹配");
        return extendApplyMapper.selectByStageId(stageId);
    }

    /**
     * 整单延时记录（申请延时 + 自主延时，含各轮，携 stageOrder），按发生时间倒序；仅任务双方可查。
     */
    public List<SysDemandExtendApply> selectExtendHistoryByDemand(Long demandId, Long userId)
    {
        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("任务不存在");
        boolean isBuyer = userId != null && userId.equals(demand.getClientId());
        boolean isCreator = userId != null && userId.equals(demand.getAcceptedCreatorId());
        if (!isBuyer && !isCreator)
        {
            throw new RuntimeException("无权查看该任务的延时记录");
        }
        return extendApplyMapper.selectByDemandId(demandId);
    }

    /**
     * 管理端整单延时记录：运营要看的是「谁申请、是否压着未处理」，不受买卖双方身份限制。
     */
    public List<SysDemandExtendApply> selectExtendHistoryForAdmin(Long demandId)
    {
        return extendApplyMapper.selectByDemandId(demandId);
    }

    /**
     * 自主延时（需求方动作，现由平台运营在管理端执行）：不经申请直接顺延活动轮截止时间，
     * 同时往延时记录表落一行（apply_type=self、写入即生效、due_after=新截止），与申请延时同格式展示。
     *
     * <p>授权来自管理端 trade:demand:edit 权限，服务层不再比对 client_id。</p>
     */
    @Transactional
    public SysDemandStage selfExtend(Long demandId, Long stageId, Long operatorId, int days, int hours)
    {
        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("任务不存在");
        if (!DEMAND_STATUS_IN_PROGRESS.equals(demand.getStatus()))
        {
            throw new RuntimeException("任务不在进行中，无法延时");
        }
        SysDemandStage stage = requireActiveStage(demandId, stageId);
        if (days < 0 || hours < 0 || days + hours <= 0)
        {
            throw new RuntimeException("延时时长（小时）必须大于0");
        }
        // 与申请延时同口径：已提交/AI审核中计时已暂停，此时挪 due_at 会与恢复计时的补时叠加
        requireDeliverableStage(stage);
        if (stage.getDueAt() == null) throw new RuntimeException("该轮尚未激活，无截止时间可顺延");
        // 存在待处理申请时先自延再审核会造成双重顺延，一次动作只做一件事
        extendApplyMapper.lockStage(stageId);
        if (extendApplyMapper.selectPendingByStageId(stageId) != null)
        {
            throw new RuntimeException("该轮有待处理的延时申请，请先处理");
        }
        Date newDue = plusDuration(stage.getDueAt(), days, hours);
        stageMapper.pushDue(stageId, newDue, String.valueOf(operatorId));
        demandMapper.mirrorRoundState(demandId, newDue, 0);
        // 自主延时同归入延时记录（无需处理，写入即已生效）；轮次行 extend_* 快照不写，避免误开审核入口
        SysDemandExtendApply self = new SysDemandExtendApply();
        self.setDemandId(demandId);
        self.setStageId(stageId);
        self.setCreatorId(operatorId);
        self.setDays(days);
        self.setHours(hours);
        self.setStatus(SysDemandExtendApply.STATUS_APPROVED);
        self.setApplyType(SysDemandExtendApply.TYPE_SELF);
        self.setDecidedBy(operatorId);
        self.setDueAfter(newDue);
        extendApplyMapper.insert(self);
        notifyService.notifyExtendSelfDone(self.getApplyId(), demandId, demand.getTitle(),
                stage.getStageOrder(), days, hours, newDue, demand.getAcceptedCreatorId());
        return stageMapper.selectByStageId(stageId);
    }

    /** 校验目标轮为当前活动轮，返回该轮（含状态与截止时间）。 */
    private SysDemandStage requireActiveStage(Long demandId, Long stageId)
    {
        SysDemandStage active = stageMapper.selectActiveStage(demandId);
        if (active == null || !active.getStageId().equals(stageId))
        {
            throw new RuntimeException("只能对当前进行中的轮次操作延时");
        }
        return active;
    }

    /** 延时只针对尚在计时的轮次（待交付/已驳回）；已提交与 AI 审核中均不开放。 */
    private void requireDeliverableStage(SysDemandStage stage)
    {
        if (!SysDemandStage.STAGE_STATUS_PENDING.equals(stage.getStatus())
                && !SysDemandStage.STAGE_STATUS_REJECTED.equals(stage.getStatus()))
        {
            throw new RuntimeException("当前轮次处于交付审核中，无需延时");
        }
    }

    /** 起点 + days 天 + hours 小时。 */
    private static Date plusDuration(Date start, int days, int hours)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(start);
        cal.add(Calendar.DAY_OF_MONTH, days);
        cal.add(Calendar.HOUR_OF_DAY, hours);
        return cal.getTime();
    }

    /** 可交付作品列表（本人的、非 on_shelf）。 */
    public List<Map<String, Object>> selectDeliverableWorks(Long creatorId)
    {
        return stageMapper.selectDeliverableWorks(creatorId);
    }

    /**
     * 查询任务的验收轮次列表。
     */
    public List<SysDemandStage> selectStagesByDemandId(Long demandId)
    {
        return stageMapper.selectByDemandId(demandId);
    }

    /**
     * 查看某轮交付作品（验收动作，现由平台运营在管理端执行）。
     *
     * <p>为什么要在 trade 域开这个口：交付作品一定未上架（deliverStage 拒 on_shelf 交付），
     * 而 content 域给读者的 /works/{id} 与 /works/{id}/chapters 走 readerVisibleWork 过滤只放行上架作品，
     * 给作者的 /content/user/works/{id} 又只认本人，需求方两条都取不到（404 / SecurityException）。
     *
     * <p>可见范围只到本轮 episodeTo：一章即一集（sys_work.episode_count 由 sys_work_chapter 行数汇总），
     * 正文截断在 SQL 的 CASE 里完成；超范围的章节保留标题且 readable=false，
     * 让验收方知道后面还有几集未交，但拿不到内容。
     *
     * <p>下发前统一过 ReviewContentText.plain：与送 AI 审核同一口径，验收方看到的就是 AI 依据的那份文本，
     * 同时避免把富文本原样丢给前端再渲染（XSS 面）。
     *
     * <p>授权来自管理端 trade:demand:query 权限，服务层不再比对 client_id（需求方已不是平台账号）。
     */
    public Map<String, Object> selectStageDeliveredWork(Long demandId, Long stageId)
    {
        SysDemandStage stage = stageMapper.selectByStageId(stageId);
        if (stage == null) throw new RuntimeException("轮次不存在");
        if (!demandId.equals(stage.getDemandId())) throw new RuntimeException("轮次与任务不匹配");

        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("任务不存在");
        if (stage.getWorkId() == null)
        {
            // 分镜任务允许纯文件/说明交付：无作品时返回交付说明与文件链接供验收
            if (TASK_TYPE_STORYBOARD.equals(demand.getTaskType())
                    && (stage.getDeliverableUrl() != null || stage.getDeliverableDesc() != null))
            {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("stageId", stageId);
                result.put("stageOrder", stage.getStageOrder());
                result.put("episodeTo", stage.getEpisodeTo());
                result.put("stageStatus", stage.getStatus());
                result.put("submittedAt", stage.getSubmittedAt());
                result.put("deliverableDesc", stage.getDeliverableDesc());
                result.put("deliverableUrl", stage.getDeliverableUrl());
                result.put("chapters", new ArrayList<>());
                result.put("readableCount", 0);
                return result;
            }
            throw new RuntimeException("本轮尚未交付作品");
        }

        Map<String, Object> work = stageMapper.selectDeliveredWorkBrief(stage.getWorkId());
        if (work == null) throw new RuntimeException("交付作品不存在或已删除");

        List<Map<String, Object>> rawChapters = stageMapper.selectChaptersForReview(stage.getWorkId(), stage.getEpisodeTo());
        List<Map<String, Object>> chapters = new ArrayList<>();
        int readableCount = 0;
        if (rawChapters != null)
        {
            for (Map<String, Object> row : rawChapters)
            {
                // content 为 NULL 时 MyBatis 不会把 key 放进 HashMap，这里逐字段补齐，前端拿到稳定结构
                Object readable = row.get("readable");
                boolean canRead = readable != null && !"0".equals(String.valueOf(readable));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("chapterId", row.get("chapterId"));
                item.put("chapterNo", row.get("chapterNo"));
                item.put("chapterTitle", row.get("chapterTitle"));
                item.put("wordCount", row.get("wordCount"));
                item.put("readable", canRead);
                item.put("content", canRead ? ReviewContentText.plain((String) row.get("content")) : null);
                chapters.add(item);
                if (canRead) readableCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>(work);
        result.put("stageId", stageId);
        result.put("stageOrder", stage.getStageOrder());
        result.put("episodeTo", stage.getEpisodeTo());
        result.put("stageStatus", stage.getStatus());
        result.put("submittedAt", stage.getSubmittedAt());
        result.put("deliverableDesc", stage.getDeliverableDesc());
        result.put("deliverableUrl", stage.getDeliverableUrl());
        result.put("aiScore", stage.getAiScore());
        result.put("chapters", chapters);
        result.put("readableCount", readableCount);
        return result;
    }

    /**
     * 查看分镜任务的源剧本（任何登录用户可看：潜在接单者需先评估剧本再决定是否接单）。
     * 作品源：全文可读（复用验收章节查询，episodeTo 传 null 即不截断）；
     * 文件源：下发上传时落库的相对地址与文件名；两种源可同时存在。
     * 正文统一过 ReviewContentText.plain，与验收/AI 审核同一口径，避免富文本原样下发（XSS 面）。
     */
    public Map<String, Object> selectSourceScript(Long demandId)
    {
        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("任务不存在");
        if (!TASK_TYPE_STORYBOARD.equals(demand.getTaskType()))
        {
            throw new RuntimeException("该任务未提供源剧本");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("demandId", demandId);
        result.put("taskType", demand.getTaskType());
        if (demand.getSourceWorkId() != null)
        {
            Map<String, Object> work = stageMapper.selectDeliveredWorkBrief(demand.getSourceWorkId());
            if (work != null)
            {
                result.put("work", work);
                List<Map<String, Object>> rawChapters = stageMapper.selectChaptersForReview(demand.getSourceWorkId(), null);
                List<Map<String, Object>> chapters = new ArrayList<>();
                if (rawChapters != null)
                {
                    for (Map<String, Object> row : rawChapters)
                    {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("chapterId", row.get("chapterId"));
                        item.put("chapterNo", row.get("chapterNo"));
                        item.put("chapterTitle", row.get("chapterTitle"));
                        item.put("wordCount", row.get("wordCount"));
                        // 源剧本供接单方整体评估，全本可读，与轮次交付的按集数截断不同
                        item.put("readable", Boolean.TRUE);
                        item.put("content", ReviewContentText.plain((String) row.get("content")));
                        chapters.add(item);
                    }
                }
                result.put("chapters", chapters);
            }
        }
        if (demand.getSourceScriptUrl() != null)
        {
            result.put("fileUrl", demand.getSourceScriptUrl());
            result.put("fileName", demand.getSourceScriptName());
        }
        if (!result.containsKey("work") && !result.containsKey("fileUrl"))
        {
            throw new RuntimeException("源剧本已失效，请联系平台客服");
        }
        return result;
    }

    /**
     * 发布征集令（无验收轮次，兼容原调用）。
     */
    public int insertDemand(SysDemand demand)
    {
        if (demand == null)
        {
            throw new RuntimeException("征集不能为空");
        }
        validateDemandBasics(demand);
        if (demand.getClientId() == null)
        {
            demand.setClientId(SecurityUtils.getUserId());
        }
        // 任务类型与源剧本校验依赖 clientId（作品归属），须在 clientId 确定后执行
        normalizeAndValidateTaskType(demand);
        if (demand.getDemandNo() == null || demand.getDemandNo().isEmpty())
        {
            demand.setDemandNo("DM" + Seq.getId());
        }
        if (demand.getSubmissionCount() == null)
        {
            demand.setSubmissionCount(0);
        }
        if (demand.getStatus() == null || demand.getStatus().isEmpty())
        {
            demand.setStatus(DEMAND_STATUS_OPEN);
        }
        // 托管与押金不再随单创建：平台在站外与需求方结算，两列保留但落库为默认 none。
        if (demand.getRequirement() == null)
        {
            demand.setRequirement("");
        }
        if (demand.getContactInfo() == null)
        {
            demand.setContactInfo("");
        }
        // APP 调用域 principal 为 Long userId，非 ruoyi LoginUser，无法用 SecurityUtils.getUsername()。
        // 由调用方（控制器/管理端）预先设置 createBy；仅在缺省时才回退到管理端 SecurityUtils。
        if (demand.getCreateBy() == null || demand.getCreateBy().isEmpty())
        {
            demand.setCreateBy(SecurityUtils.getUsername());
        }
        if (demand.getCreateTime() == null)
        {
            demand.setCreateTime(new Date());
        }
        return demandMapper.insertDemand(demand);
    }

    /**
     * 关闭征集（运营在管理端截止）。调用方须具备 trade:demand:edit 权限，服务层不再比对发布者身份。
     */
    public int closeDemand(Long demandId, Long operatorId)
    {
        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("征集不存在");
        if (!DEMAND_STATUS_OPEN.equals(demand.getStatus())) throw new RuntimeException("当前状态不可关闭");
        // CAS：判 open 与写入之间可能被并发抢单/编辑，状态已变则放弃关闭
        int changed = demandMapper.updateStatusCas(demandId, DEMAND_STATUS_CLOSED,
                String.valueOf(operatorId), DEMAND_STATUS_OPEN);
        if (changed == 0) throw new RuntimeException("任务状态已变更，请刷新后重试");
        return changed;
    }

    /**
     * 投稿（编剧响应征集）。校验 demand open、不重复、关联自己的作品。
     */
    @Transactional
    public SysDemandSubmission insertSubmission(Long demandId, Long workId, Long submitterId, String message)
    {
        SysDemand demand = demandMapper.selectDemandById(demandId);
        if (demand == null) throw new RuntimeException("征集不存在");
        if (!DEMAND_STATUS_OPEN.equals(demand.getStatus())) throw new RuntimeException("该征集已截止，不可投稿");

        // 检查重复投稿（数据库唯一索引 uk_demand_work 也会拦截）
        List<SysDemandSubmission> existing = submissionMapper.selectSubmissionsByDemandId(demandId);
        for (SysDemandSubmission s : existing)
        {
            if (workId.equals(s.getWorkId()) && submitterId.equals(s.getSubmitterId()))
            {
                throw new RuntimeException("您已对该作品投稿，请勿重复提交");
            }
        }

        SysDemandSubmission sub = new SysDemandSubmission();
        sub.setDemandId(demandId);
        sub.setWorkId(workId);
        sub.setSubmitterId(submitterId);
        sub.setStatus(SUBMISSION_STATUS_SUBMITTED);
        sub.setSubmitMessage(message);
        sub.setCreateBy(String.valueOf(submitterId));
        sub.setCreateTime(new Date());
        submissionMapper.insertSubmission(sub);
        demandMapper.incrementSubmissionCount(demandId);

        // 通知发布者
        notifyService.notifyDemandSubmitted(sub.getSubmissionId(), demandId, demand.getTitle(), demand.getClientId());
        return sub;
    }

    /**
     * 入围。
     */
    public int shortlistSubmission(Long submissionId, Long operatorId)
    {
        SysDemandSubmission sub = submissionMapper.selectSubmissionById(submissionId);
        if (sub == null) throw new RuntimeException("投稿不存在");
        SysDemand demand = demandMapper.selectDemandById(sub.getDemandId());
        if (demand == null || !operatorId.equals(demand.getClientId())) throw new RuntimeException("无权操作");
        if (!SUBMISSION_STATUS_SUBMITTED.equals(sub.getStatus())) throw new RuntimeException("仅已投稿状态可入围");
        int changed = submissionMapper.updateStatus(submissionId, SUBMISSION_STATUS_SHORTLISTED, null, String.valueOf(operatorId));
        if (changed == 1)
        {
            notifyService.notifySubmissionShortlisted(submissionId, sub.getDemandId(), demand.getTitle(),
                    sub.getSubmitterId(), sub.getWorkTitle());
        }
        return changed;
    }

    /**
     * 选标（中标）：创建询盘 + 关闭征集 + 批量拒绝其它投稿。
     */
    @Transactional
    public SysInquiry acceptSubmission(Long submissionId, Long operatorId)
    {
        SysDemandSubmission sub = submissionMapper.selectSubmissionById(submissionId);
        if (sub == null) throw new RuntimeException("投稿不存在");
        SysDemand demand = demandMapper.selectDemandById(sub.getDemandId());
        if (demand == null || !operatorId.equals(demand.getClientId())) throw new RuntimeException("只有发布者可以选标");
        if (DEMAND_STATUS_SELECTED.equals(demand.getStatus())) throw new RuntimeException("该征集已选定");
        if (DEMAND_STATUS_CLOSED.equals(demand.getStatus())) throw new RuntimeException("该征集已截止");

        // 投稿置中标
        submissionMapper.updateStatus(submissionId, SUBMISSION_STATUS_ACCEPTED, "恭喜中标", String.valueOf(operatorId));
        // 征集置已选定
        demandMapper.updateStatus(demand.getDemandId(), DEMAND_STATUS_SELECTED, String.valueOf(operatorId));
        // 批量拒绝其它投稿
        submissionMapper.batchRejectByDemandId(demand.getDemandId(), submissionId, String.valueOf(operatorId));
        // 落选投稿逐个通知创作者
        for (SysDemandSubmission other : submissionMapper.selectSubmissionsByDemandId(demand.getDemandId()))
        {
            if (!submissionId.equals(other.getSubmissionId())
                    && SUBMISSION_STATUS_REJECTED.equals(other.getStatus()))
            {
                notifyService.notifySubmissionRejected(other.getSubmissionId(), demand.getDemandId(),
                        demand.getTitle(), other.getSubmitterId(), other.getWorkTitle(), "本征集已选定其他投稿");
            }
        }

        // 自动创建询盘（复用现有交易链路）
        SysInquiry inquiry = new SysInquiry();
        inquiry.setWorkId(sub.getWorkId());
        inquiry.setBuyerId(demand.getClientId());
        inquiry.setSellerId(sub.getSubmitterId());
        inquiry.setLicenseType("exclusive");
        inquiry.setIntendedUse("任务广场选标：" + demand.getTitle());
        inquiry.setBudget(demand.getBudget());
        inquiry.setMessage("恭喜您在任务广场中选，本询盘由系统自动创建，请进入报价流程。");
        inquiry.setCreateBy(String.valueOf(demand.getClientId()));
        SysInquiry created = inquiryService.createInquiry(inquiry);

        // 通知投稿者
        notifyService.notifySubmissionAccepted(submissionId, sub.getSubmitterId(), demand.getTitle());
        return created;
    }

    /**
     * 拒绝投稿。
     */
    public int rejectSubmission(Long submissionId, Long operatorId, String remark)
    {
        SysDemandSubmission sub = submissionMapper.selectSubmissionById(submissionId);
        if (sub == null) throw new RuntimeException("投稿不存在");
        SysDemand demand = demandMapper.selectDemandById(sub.getDemandId());
        if (demand == null || !operatorId.equals(demand.getClientId())) throw new RuntimeException("无权操作");
        if (SUBMISSION_STATUS_ACCEPTED.equals(sub.getStatus())) throw new RuntimeException("已中标不可拒绝");
        int changed = submissionMapper.updateStatus(submissionId, SUBMISSION_STATUS_REJECTED, remark, String.valueOf(operatorId));
        if (changed == 1)
        {
            notifyService.notifySubmissionRejected(submissionId, sub.getDemandId(), demand.getTitle(),
                    sub.getSubmitterId(), sub.getWorkTitle(), remark);
        }
        return changed;
    }

    private static Date startOfDay(Date d)
    {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
