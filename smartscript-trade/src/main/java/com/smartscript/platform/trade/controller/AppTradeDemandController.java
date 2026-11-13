package com.smartscript.platform.trade.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandExtendApply;
import com.smartscript.platform.trade.domain.SysDemandStage;
import com.smartscript.platform.trade.domain.SysDemandSubmission;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.service.TradeDemandService;
import com.smartscript.platform.trade.service.DemandSandboxPaymentService;
import com.smartscript.platform.user.service.ChatService;

/**
 * APP端 任务广场（需求悬赏）接口。
 *
 * 路径：/api/v1/trade/demand/*
 * 鉴权：由 APP 凭证域的 Spring Security 链完成；业务层以
 * SecurityContextHolder 中的 Authentication.getPrincipal()（Long userId）为准。
 * 金额约定：数据库以元存储，APP 交互以分传输。
 */
@RestController
@RequestMapping("/api/v1/trade/demand")
public class AppTradeDemandController
{
    private final TradeDemandService demandService;
    private final DemandSandboxPaymentService demandSandbox;
    private final IdentityProvider identityProvider;
    private final ChatService chatService;

    /** 招标制（投稿/选标/中标）已下线，任务仅支持接单制；相关端点统一返回此提示。 */
    private static final String BIDDING_OFFLINE_MSG =
            "招标制已下线，本任务仅支持接单制（付押金接单、逐轮交付验收）";

    /** 开发直通托管开关：yml 键 app.demand.escrow.mock-pass（默认开），环境变量 APP_DEMAND_ESCROW_MOCK_PASS 可覆盖。 */
    @Value("${app.demand.escrow.mock-pass:true}")
    private boolean mockPassEnabled;

    /** 运行环境标识：prod 时直通端点强制失效，防止生产误开。 */
    @Value("${app.auth.env:local}")
    private String appAuthEnv;

    /** 直通可用性判定：返回 null 表示允许直通，否则返回拒绝原因。 */
    private String devPassBlockReason()
    {
        if ("prod".equalsIgnoreCase(appAuthEnv))
        {
            return "生产环境已强制关闭开发直通，请使用正式支付";
        }
        if (!mockPassEnabled)
        {
            return "开发直通未开启，请使用支付宝沙箱付款";
        }
        return null;
    }

    public AppTradeDemandController(TradeDemandService demandService, DemandSandboxPaymentService demandSandbox,
            IdentityProvider identityProvider, ChatService chatService)
    {
        this.demandService = demandService;
        this.demandSandbox = demandSandbox;
        this.identityProvider = identityProvider;
        this.chatService = chatService;
    }

    /**
     * 任务列表（分页，返回 {total, list}）。
     *
     * <p>接单制：不传 mine 时为「任务广场」，仅展示可抢的单（status=open），已被接单/完成不再对外展示；
     * mine=published 查我发布的（需登录），mine=claimed 查我接取的（需登录），两者返回全部状态。</p>
     */
    @GetMapping("/list")
    public AppApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer genreId,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String mine,
            @RequestParam(required = false, defaultValue = "created") String sortBy,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Long budgetMin,
            @RequestParam(required = false) Long budgetMax,
            @RequestParam(required = false) Integer episodesMin,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize)
    {
        boolean authenticated = identityProvider.currentIdentity().isAuthenticated();
        if (!authenticated && mine != null && !mine.isBlank())
        {
            return AppApiResponse.fail(401, "请登录后查看个人任务");
        }
        SysDemand query = new SysDemand();
        if (genreId != null) query.setGenreId(genreId);
        if (taskType != null && !taskType.isEmpty()) query.setTaskType(taskType);
        if (keyword != null && !keyword.isEmpty()) query.setKeyword(keyword);
        if ("published".equals(mine))
        {
            query.setClientId(currentUserId());
            if (status != null && !status.isEmpty()) query.setStatus(status);
        }
        else if ("claimed".equals(mine))
        {
            query.setAcceptedCreatorId(currentUserId());
            if (status != null && !status.isEmpty()) query.setStatus(status);
        }
        else
        {
            // 任务广场：仅展示可接取的任务（open+已托管）；自己发布的/已接的其他任务只在"我的任务"查看，
            // 不再并入广场，也不泄露他人未托管单。游客与登录用户同口径。
            query.setStatus(TradeDemandService.DEMAND_STATUS_OPEN);
            query.setEscrowStatus(TradeDemandService.ESCROW_ESCROWED);
        }
        // 排序（白名单键，非法值由 mapper 回落 created desc）；预算/集数筛选 APP 传分→元
        query.setSortBy(sortBy);
        query.setSortDir(order);
        if (budgetMin != null) query.setBudgetMin(fenToYuan(BigDecimal.valueOf(budgetMin)));
        if (budgetMax != null) query.setBudgetMax(fenToYuan(BigDecimal.valueOf(budgetMax)));
        if (episodesMin != null) query.setEpisodesMin(episodesMin);
        // 真分页：clamp 页大小，独立 count + SQL LIMIT/OFFSET（不再全表捐进内存）
        int safePageSize = (pageSize == null || pageSize <= 0 || pageSize > 100) ? 20 : pageSize;
        int safePageNum = (pageNum == null || pageNum <= 0) ? 1 : pageNum;
        query.setLimit(safePageSize);
        query.setOffset((safePageNum - 1) * safePageSize);
        long total = demandService.countDemandList(query);
        List<SysDemand> list = demandService.selectDemandPage(query);
        // 元→分
        for (SysDemand d : list)
        {
            applyFen(d);
        }
        List<?> responseList = list;
        if (!authenticated)
        {
            // 游客只拿到公开任务展示字段，避免原始 SysDemand 带出联系方式、用户编号及资金状态。
            List<Map<String, Object>> publicList = new ArrayList<>(list.size());
            for (SysDemand demand : list)
            {
                publicList.add(publicView(demand));
            }
            responseList = publicList;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("list", responseList);
        return AppApiResponse.ok(result);
    }

    /**
     * 任务广场轻量统计（供 Hero 横幅与题材 chip 角标）。
     *
     * <p>广场与角标统一"公开可接单"口径（status=open ∧ escrow=escrowed），与登录无关：
     * availableCount/prizePoolFen/todayNewCount/genreCounts/typeCounts 均按此口径，
     * 角标总数=可接单总数，与广场列表可见集合一致。</p>
     */
    @GetMapping("/stats")
    public AppApiResponse<Map<String, Object>> stats()
    {
        Map<String, Object> agg = demandService.selectDemandSquareStats();
        long availableCount = toLong(agg == null ? null : agg.get("availableCount"));
        BigDecimal prizePoolYuan = toBigDecimal(agg == null ? null : agg.get("prizePoolYuan"));
        long todayNewCount = toLong(agg == null ? null : agg.get("todayNewCount"));
        List<Map<String, Object>> genreRows = demandService.selectGenreCounts();
        List<Map<String, Object>> genreCounts = new ArrayList<>();
        for (Map<String, Object> row : genreRows)
        {
            Map<String, Object> item = new HashMap<>();
            item.put("genreId", row.get("genreId"));
            item.put("count", toLong(row.get("cnt")));
            genreCounts.add(item);
        }
        List<Map<String, Object>> typeRows = demandService.selectTypeCounts();
        List<Map<String, Object>> typeCounts = new ArrayList<>();
        for (Map<String, Object> row : typeRows)
        {
            Map<String, Object> item = new HashMap<>();
            item.put("taskType", row.get("taskType"));
            item.put("count", toLong(row.get("cnt")));
            typeCounts.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("availableCount", availableCount);
        // 与 /list 一致：聚合 SQL 按元求和，出参统一转分（APP 以分交互的全局约定）
        result.put("prizePoolFen", yuanToFen(prizePoolYuan));
        result.put("todayNewCount", todayNewCount);
        result.put("genreCounts", genreCounts);
        result.put("typeCounts", typeCounts);
        return AppApiResponse.ok(result);
    }

    private static long toLong(Object v)
    {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return 0L; }
    }

    private static BigDecimal toBigDecimal(Object v)
    {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        try { return new BigDecimal(String.valueOf(v)); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    /**
     * 任务详情。
     *
     * <p>游客可看，但可见集合与广场列表严格一致（status=open ∧ 已托管），其余状态按「不存在」处理；
     * 且只回公开字段。原始 SysDemand 含联系方式、发布者编号、托管/押金金额与状态、源剧本地址，
     * 这些只在登录后返回（登录口径保持不变，任务双方仍能看到自己需要的字段）。</p>
     */
    @GetMapping("/{demandId}")
    public AppApiResponse<?> detail(@PathVariable Long demandId)
    {
        SysDemand demand = demandService.selectDemandById(demandId);
        if (demand == null)
        {
            return AppApiResponse.fail(404, "任务不存在");
        }
        applyFen(demand);
        if (!identityProvider.currentIdentity().isAuthenticated())
        {
            boolean squareVisible = TradeDemandService.DEMAND_STATUS_OPEN.equals(demand.getStatus())
                    && TradeDemandService.ESCROW_ESCROWED.equals(demand.getEscrowStatus());
            if (!squareVisible)
            {
                // 不用 403：403 会告诉调用方「这个单存在但你没权限」，任务编号本身也是业务信息。
                return AppApiResponse.fail(404, "任务不存在");
            }
            return AppApiResponse.ok(publicView(demand));
        }
        return AppApiResponse.ok(demand);
    }

    /** 游客视角的任务公开字段；与 /list 的游客口径共用，避免两处漂移。 */
    private static Map<String, Object> publicView(SysDemand demand)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("demandId", demand.getDemandId());
        item.put("demandNo", demand.getDemandNo());
        item.put("title", demand.getTitle());
        item.put("genreId", demand.getGenreId());
        item.put("tags", demand.getTags());
        item.put("budget", demand.getBudget());
        item.put("deadline", demand.getDeadline());
        item.put("requirement", demand.getRequirement());
        item.put("totalEpisodes", demand.getTotalEpisodes());
        item.put("durationHours", demand.getDurationHours());
        item.put("status", demand.getStatus());
        item.put("createdAt", demand.getCreatedAt());
        item.put("taskType", demand.getTaskType());
        item.put("sourceScriptName", demand.getSourceScriptName());
        item.put("sourceWorkTitle", demand.getSourceWorkTitle());
        item.put("clientName", demand.getClientName());
        // 到这里必然已托管，回这个标记只为让客户端状态渲染不缺项；金额与押金状态一律不回。
        item.put("escrowStatus", demand.getEscrowStatus());
        return item;
    }

    /**
     * 发布任务（企业/买方角色）。
     * Body: {title, genreId, budget(分), deadline, requirement, contactInfo, tags, totalEpisodes,
     *        taskType(script/script_storyboard/storyboard), sourceWorkId, sourceScriptUrl, sourceScriptName,
     *        stages:[{stageOrder,episodeTo,amount(元)}]}
     */
    @PostMapping
    public AppApiResponse<SysDemand> create(@RequestBody Map<String, Object> body)
    {
        Long userId = currentUserId();
        SysDemand demand = new SysDemand();
        demand.setClientId(userId);
        demand.setCreateBy(String.valueOf(userId));
        demand.setTitle(str(body, "title"));
        demand.setGenreId(intVal(body, "genreId"));
        demand.setBudget(fenToYuan(decimalVal(body, "budget")));
        demand.setTags(str(body, "tags"));
        demand.setRequirement(str(body, "requirement"));
        demand.setContactInfo(str(body, "contactInfo"));
        demand.setTotalEpisodes(intVal(body, "totalEpisodes"));
        demand.setDurationHours(intVal(body, "durationHours"));
        demand.setTaskType(str(body, "taskType"));
        demand.setSourceWorkId(longVal(body, "sourceWorkId"));
        demand.setSourceScriptUrl(str(body, "sourceScriptUrl"));
        demand.setSourceScriptName(str(body, "sourceScriptName"));
        // 总集数必填：最后一轮验收依赖总集数固定生成
        if (demand.getTotalEpisodes() == null || demand.getTotalEpisodes() <= 0)
        {
            return AppApiResponse.fail(400, "请填写总集数");
        }
        String deadlineStr = str(body, "deadline");
        if (deadlineStr != null && !deadlineStr.isEmpty())
        {
            try
            {
                demand.setDeadline(new java.text.SimpleDateFormat("yyyy-MM-dd").parse(deadlineStr));
            }
            catch (java.text.ParseException e)
            {
                return AppApiResponse.fail(400, "截止日期格式不正确，请重新选择");
            }
        }

        // 解析验收轮次；未传轮次时由 Service 固定兜底生成最后一轮验收
        List<SysDemandStage> stages = parseStages(body.get("stages"));
        try
        {
            demandService.insertDemand(demand, stages);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
        applyFen(demand);
        return AppApiResponse.ok(demand);
    }

    /**
     * 编辑任务（仅发布者、仅未托管未接单可改）。
     * Body 同发布：{title, genreId, budget(分), deadline, requirement, contactInfo, tags, totalEpisodes, durationHours,
     *              taskType, sourceWorkId, sourceScriptUrl, sourceScriptName, stages:[...]}。
     * 不传 taskType 时沿用原任务类型与源剧本（兼容旧客户端）。
     */
    @PutMapping("/{demandId}")
    public AppApiResponse<SysDemand> update(@PathVariable Long demandId, @RequestBody Map<String, Object> body)
    {
        Long userId = currentUserId();
        SysDemand demand = new SysDemand();
        demand.setDemandId(demandId);
        demand.setTitle(str(body, "title"));
        demand.setGenreId(intVal(body, "genreId"));
        demand.setBudget(fenToYuan(decimalVal(body, "budget")));
        demand.setTags(str(body, "tags"));
        demand.setRequirement(str(body, "requirement"));
        demand.setContactInfo(str(body, "contactInfo"));
        demand.setTotalEpisodes(intVal(body, "totalEpisodes"));
        demand.setDurationHours(intVal(body, "durationHours"));
        demand.setTaskType(str(body, "taskType"));
        demand.setSourceWorkId(longVal(body, "sourceWorkId"));
        demand.setSourceScriptUrl(str(body, "sourceScriptUrl"));
        demand.setSourceScriptName(str(body, "sourceScriptName"));
        // 总集数必填：最后一轮验收依赖总集数固定生成
        if (demand.getTotalEpisodes() == null || demand.getTotalEpisodes() <= 0)
        {
            return AppApiResponse.fail(400, "请填写总集数");
        }
        String deadlineStr = str(body, "deadline");
        if (deadlineStr != null && !deadlineStr.isEmpty())
        {
            try
            {
                demand.setDeadline(new java.text.SimpleDateFormat("yyyy-MM-dd").parse(deadlineStr));
            }
            catch (java.text.ParseException e)
            {
                return AppApiResponse.fail(400, "截止日期格式不正确，请重新选择");
            }
        }
        List<SysDemandStage> stages = parseStages(body.get("stages"));
        try
        {
            SysDemand d = demandService.updateDemand(demand, stages, userId);
            applyFen(d);
            return AppApiResponse.ok(d);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 关闭征集（发布者手动截止）。
     */
    @PutMapping("/{demandId}/close")
    public AppApiResponse<Void> close(@PathVariable Long demandId)
    {
        Long userId = currentUserId();
        demandService.closeDemand(demandId, userId);
        return AppApiResponse.ok();
    }

    /**
     * 抢单（创作者先到先得接单）。
     *
     * <p>正常接单走 {@code pay/deposit → confirm}（付押金即接单）；本端点仅作兜底：
     * 押金已入账（deposit_status=held，例如线下收款/沙箱已确认）时可直接完成接单占位，
     * 未付押金则引导前端先走 pay/deposit。</p>
     */
    @PostMapping("/{demandId}/claim")
    public AppApiResponse<SysDemand> claim(@PathVariable Long demandId)
    {
        if (!hasCreatorCapability())
        {
            return AppApiResponse.fail(403, "请先开通创作者资格再接单");
        }
        Long userId = currentUserId();
        SysDemand existing = demandService.selectDemandById(demandId);
        if (existing == null)
        {
            return AppApiResponse.fail(404, "任务不存在");
        }
        if (!TradeDemandService.DEPOSIT_HELD.equals(existing.getDepositStatus()))
        {
            return AppApiResponse.fail(400, "请先支付押金再接单");
        }
        SysDemand demand = demandService.claimDemand(demandId, userId);
        applyFen(demand);
        return AppApiResponse.ok(demand);
    }

    /**
     * 创建/获取「任务会话」（发布者 ↔ 创作者），接单前后均可就本任务沟通。
     *
     * <p>创作者发起：以本人为创作者，需已开通创作者资格，对方为任务发布者；
     * 发布者发起：须带 targetUserId（要沟通的创作者）。会话按
     * (发布者, 创作者, 任务) 幂等唯一，返回体为会话 VO（含 sessionId）。</p>
     */
    @PostMapping("/{demandId}/chat/session")
    public AppApiResponse<Map<String, Object>> chatSession(
            @PathVariable Long demandId,
            @RequestParam(required = false) Long targetUserId)
    {
        Long userId = currentUserId();
        SysDemand demand = demandService.selectDemandById(demandId);
        if (demand == null) return AppApiResponse.fail(404, "任务不存在");
        Long publisher = demand.getClientId();
        if (publisher == null) return AppApiResponse.fail(400, "任务缺少发布者");
        Long creator;
        if (userId.equals(publisher))
        {
            if (targetUserId == null) return AppApiResponse.fail(400, "请指定要沟通的创作者");
            creator = targetUserId;
        }
        else
        {
            if (!hasCreatorCapability()) return AppApiResponse.fail(403, "请先开通创作者资格再沟通");
            creator = userId;
        }
        try
        {
            Map<String, Object> vo = chatService.createOrGetDemandSession(userId, demandId, publisher, creator, demand.getTitle());
            return AppApiResponse.ok(vo);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /* ===================== 支付宝沙箱入账（托管/押金） ===================== */

    /**
     * 发起托管付款（发布者）：生成扫码收款二维码，将预算全额付入平台第三方托管。
     */
    @PostMapping("/{demandId}/pay/escrow")
    public AppApiResponse<Map<String, Object>> payEscrow(@PathVariable Long demandId,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh)
    {
        try { return AppApiResponse.ok(payFen(demandSandbox.createEscrow(demandId, currentUserId(), refresh))); }
        catch (IllegalArgumentException | IllegalStateException e) { return AppApiResponse.fail(400, e.getMessage()); }
        catch (RuntimeException e) { return AppApiResponse.fail(500, "沙箱支付暂不可用"); }
    }

    /**
     * 确认托管付款：以支付宝主动查单为准，成功后置 escrow_status=escrowed（任务方对外可抢）。
     */
    @PostMapping("/{demandId}/pay/escrow/confirm")
    public AppApiResponse<Map<String, Object>> confirmEscrow(@PathVariable Long demandId)
    {
        try { return AppApiResponse.ok(demandSandbox.confirmEscrow(demandId, currentUserId())); }
        catch (IllegalArgumentException | IllegalStateException e) { return AppApiResponse.fail(400, e.getMessage()); }
        catch (RuntimeException e) { return AppApiResponse.fail(500, "沙箱支付暂不可用"); }
    }

    /**
     * 开发直通托管（"点一下即完成"）：不走支付宝沙箱查单，直接把托管置为已到账。
     *
     * <p>受配置 {@code app.demand.escrow.mock-pass}（环境变量 APP_DEMAND_ESCROW_MOCK_PASS，默认开）
     * 控制，且 {@code APP_AUTH_ENV=prod} 时强制失效，供沙箱密钥
     * 尚未接入时打通"发布→托管→广场曝光"链路。正式扫码收款请由队友接入上方
     * {@code /pay/escrow} + {@code /pay/escrow/confirm} 两个沙箱端点。</p>
     */
    @PostMapping("/{demandId}/pay/escrow/dev-pass")
    public AppApiResponse<SysDemand> devPassEscrow(@PathVariable Long demandId)
    {
        String blocked = devPassBlockReason();
        if (blocked != null) return AppApiResponse.fail(400, blocked);
        try
        {
            SysDemand d = demandService.mockPassEscrow(demandId, currentUserId());
            applyFen(d);
            return AppApiResponse.ok(d);
        }
        catch (RuntimeException e) { return AppApiResponse.fail(400, e.getMessage()); }
    }

    /**
     * 发起押金付款（创作者）：生成扫码收款二维码，付完押金后调 confirm 自动接单。
     */
    @PostMapping("/{demandId}/pay/deposit")
    public AppApiResponse<Map<String, Object>> payDeposit(@PathVariable Long demandId,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh)
    {
        if (!hasCreatorCapability()) return AppApiResponse.fail(403, "请先开通创作者资格再接单");
        try { return AppApiResponse.ok(payFen(demandSandbox.createDeposit(demandId, currentUserId(), refresh))); }
        catch (IllegalArgumentException | IllegalStateException e) { return AppApiResponse.fail(400, e.getMessage()); }
        catch (RuntimeException e) { return AppApiResponse.fail(500, "沙箱支付暂不可用"); }
    }

    /**
     * 确认押金付款：查单成功后原子执行抢单（deposit_status=held + 激活首轮）。
     */
    @PostMapping("/{demandId}/pay/deposit/confirm")
    public AppApiResponse<Map<String, Object>> confirmDeposit(@PathVariable Long demandId)
    {
        if (!hasCreatorCapability()) return AppApiResponse.fail(403, "请先开通创作者资格再接单");
        try { return AppApiResponse.ok(demandSandbox.confirmDeposit(demandId, currentUserId())); }
        catch (IllegalArgumentException | IllegalStateException e) { return AppApiResponse.fail(400, e.getMessage()); }
        catch (RuntimeException e) { return AppApiResponse.fail(500, "沙箱支付暂不可用"); }
    }

    /**
     * 开发直通押金（“点一下即付款接单”）：不走支付宝沙箱查单，直接记账押金并原子完成抢单。
     *
     * <p>与发布者托管直通共用开关 {@code app.demand.escrow.mock-pass}，处理口径对齐：
     * 本地/测试环境沙箱密钥未接入时一键打通“付押金→接单”；正式收款请走
     * {@code /pay/deposit} + {@code /pay/deposit/confirm} 两个沙箱端点。</p>
     */
    @PostMapping("/{demandId}/pay/deposit/dev-pass")
    public AppApiResponse<SysDemand> devPassDeposit(@PathVariable Long demandId)
    {
        String blocked = devPassBlockReason();
        if (blocked != null) return AppApiResponse.fail(400, blocked);
        if (!hasCreatorCapability()) return AppApiResponse.fail(403, "请先开通创作者资格再接单");
        try
        {
            SysDemand d = demandService.claimDemand(demandId, currentUserId());
            applyFen(d);
            return AppApiResponse.ok(d);
        }
        catch (RuntimeException e) { return AppApiResponse.fail(400, e.getMessage()); }
    }

    /**
     * 查看投稿列表（仅发布者可看）。
     * 招标制已下线：任务仅支持接单制，投稿/选标链路整体停用（service 与库表保留休眠，可回退）。
     */
    @GetMapping("/{demandId}/submissions")
    public AppApiResponse<List<SysDemandSubmission>> submissions(@PathVariable Long demandId)
    {
        return AppApiResponse.fail(410, BIDDING_OFFLINE_MSG);
    }

    /**
     * 投稿（卖方/编剧响应征集）。
     * Body: {workId, message}
     */
    @PostMapping("/{demandId}/submit")
    public AppApiResponse<SysDemandSubmission> submit(
            @PathVariable Long demandId,
            @RequestBody Map<String, Object> body)
    {
        return AppApiResponse.fail(410, BIDDING_OFFLINE_MSG);
    }

    /**
     * 入围。
     */
    @PutMapping("/submission/{submissionId}/shortlist")
    public AppApiResponse<Void> shortlist(@PathVariable Long submissionId)
    {
        return AppApiResponse.fail(410, BIDDING_OFFLINE_MSG);
    }

    /**
     * 选标（中标）。
     */
    @PutMapping("/submission/{submissionId}/accept")
    public AppApiResponse<SysInquiry> accept(@PathVariable Long submissionId)
    {
        return AppApiResponse.fail(410, BIDDING_OFFLINE_MSG);
    }

    /**
     * 拒绝投稿。
     * Body: {remark}（可选）
     */
    @PutMapping("/submission/{submissionId}/reject")
    public AppApiResponse<Void> reject(
            @PathVariable Long submissionId,
            @RequestBody(required = false) Map<String, Object> body)
    {
        return AppApiResponse.fail(410, BIDDING_OFFLINE_MSG);
    }

    /**
     * 我的投稿记录（编剧查看自己所有投稿）。
     */
    @GetMapping("/my-submissions")
    public AppApiResponse<List<SysDemandSubmission>> mySubmissions()
    {
        return AppApiResponse.fail(410, BIDDING_OFFLINE_MSG);
    }

    /* ==================== 验收轮次 ==================== */

    /**
     * 查看任务的验收轮次列表。
     */
    @GetMapping("/{demandId}/stages")
    public AppApiResponse<List<SysDemandStage>> stages(@PathVariable Long demandId)
    {
        List<SysDemandStage> list = demandService.selectStagesByDemandId(demandId);
        // 元→分
        for (SysDemandStage s : list)
        {
            s.setAmount(yuanToFen(s.getAmount()));
        }
        return AppApiResponse.ok(list);
    }

    /**
     * 可交付作品列表（接单创作者选作：本人的、非已上架售卖）。
     */
    @GetMapping("/deliverable-works")
    public AppApiResponse<List<Map<String, Object>>> deliverableWorks()
    {
        return AppApiResponse.ok(demandService.selectDeliverableWorks(currentUserId()));
    }

    /**
     * 源剧本作品候选（发布分镜任务时选择本人作品；本人未删除作品即可，不排除已上架）。
     */
    @GetMapping("/source-works")
    public AppApiResponse<List<Map<String, Object>>> sourceWorks()
    {
        return AppApiResponse.ok(demandService.selectSourceWorks(currentUserId()));
    }

    /**
     * 上传源剧本文件（发布分镜任务用，发布者无需创作者资格）。
     * multipart 字段名 file；返回 {url(相对路径，客户端按 baseUrl 解析), fileName}。
     */
    @PostMapping("/upload-script")
    public AppApiResponse<Map<String, Object>> uploadScript(@RequestParam("file") org.springframework.web.multipart.MultipartFile file)
    {
        try
        {
            return AppApiResponse.ok(demandService.uploadSourceScript(file));
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 查看分镜任务的源剧本（任何登录用户可看：潜在接单者先评估剧本再决定是否付押金）。
     * 返回 {demandId, taskType, work?, chapters?, fileUrl?, fileName?}：作品源给全文，文件源给下载地址。
     */
    @GetMapping("/{demandId}/source-script")
    public AppApiResponse<Map<String, Object>> sourceScript(@PathVariable Long demandId)
    {
        try
        {
            return AppApiResponse.ok(demandService.selectSourceScript(demandId));
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 创作者提交本轮交付物（提交后本轮计时暂停待审核）。
     * Body: {workId, deliverableDesc, deliverableUrl}
     * 剧本类任务 workId 必填；分镜类（storyboard）作品可选，未选作品时须给文件链接或交付说明，
     * 且跳过 AI 审核直接进入人工验收。
     */
    @PostMapping("/{demandId}/stage/{stageId}/deliver")
    public AppApiResponse<SysDemandStage> deliver(
            @PathVariable Long demandId,
            @PathVariable Long stageId,
            @RequestBody Map<String, Object> body)
    {
        Long userId = currentUserId();
        String desc = str(body, "deliverableDesc");
        String url = str(body, "deliverableUrl");
        Long workId = longVal(body, "workId");
        try
        {
            SysDemandStage stage = demandService.deliverStage(demandId, stageId, userId, desc, url, workId);
            stage.setAmount(yuanToFen(stage.getAmount()));
            return AppApiResponse.ok(stage);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 买方审核轮次。通过→释放并激活下一轮；驳回→计时恢复且截止顺延审核耗时。
     * Body: {approved: true/false, rejectReason(可选)}
     */
    @PutMapping("/{demandId}/stage/{stageId}/review")
    public AppApiResponse<SysDemandStage> review(
            @PathVariable Long demandId,
            @PathVariable Long stageId,
            @RequestBody Map<String, Object> body)
    {
        Long userId = currentUserId();
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String rejectReason = str(body, "rejectReason");
        try
        {
            SysDemandStage stage = demandService.reviewStage(demandId, stageId, userId, approved, rejectReason);
            stage.setAmount(yuanToFen(stage.getAmount()));
            return AppApiResponse.ok(stage);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 发布者查看某轮交付作品（验收前核对正文）。
     *
     * 仅该任务发布者可调；正文只下发本轮 episodeTo 内的章节，
     * 之后章节只给标题并标 readable=false（交付作品未上架，走不了 content 域的读者接口）。
     */
    @GetMapping("/{demandId}/stage/{stageId}/deliverable-work")
    public AppApiResponse<Map<String, Object>> deliverableWork(
            @PathVariable Long demandId,
            @PathVariable Long stageId)
    {
        Long userId = currentUserId();
        try
        {
            return AppApiResponse.ok(demandService.selectStageDeliveredWork(demandId, stageId, userId));
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 创作者对活动轮发起延时申请（每次新增一条历史记录，既往申请不覆盖；同轮同时只允许一个待处理）。
     * Body: {hours, reason(可选)}，前端只填小时；兼容按天数传的旧客户端（按 days*24+hours 折算，须大于 0）
     */
    @PostMapping("/{demandId}/stage/{stageId}/extend/request")
    public AppApiResponse<SysDemandStage> requestExtend(
            @PathVariable Long demandId,
            @PathVariable Long stageId,
            @RequestBody Map<String, Object> body)
    {
        Long userId = currentUserId();
        int days = intOrZero(body, "days");
        int hours = intOrZero(body, "hours");
        String reason = str(body, "reason");
        try
        {
            SysDemandStage stage = demandService.requestExtend(demandId, stageId, userId, days, hours, reason);
            stage.setAmount(yuanToFen(stage.getAmount()));
            return AppApiResponse.ok(stage);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 发布者处理延时申请：只处理当前待处理那条记录（处理完永久保留），同意则截止同步顺延，不同意仅落决定。
     * Body: {approved: true/false}
     */
    @PutMapping("/{demandId}/stage/{stageId}/extend/decision")
    public AppApiResponse<SysDemandStage> decideExtend(
            @PathVariable Long demandId,
            @PathVariable Long stageId,
            @RequestBody Map<String, Object> body)
    {
        Long userId = currentUserId();
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        try
        {
            SysDemandStage stage = demandService.decideExtend(demandId, stageId, userId, approved);
            stage.setAmount(yuanToFen(stage.getAmount()));
            return AppApiResponse.ok(stage);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 某轮延时记录（申请延时 + 自主延时）：每次动作一行，通过/驳回均保留（按发生时间倒序）。
     */
    @GetMapping("/{demandId}/stage/{stageId}/extend/history")
    public AppApiResponse<List<SysDemandExtendApply>> extendHistory(
            @PathVariable Long demandId,
            @PathVariable Long stageId)
    {
        try
        {
            return AppApiResponse.ok(demandService.selectExtendHistory(demandId, stageId, currentUserId()));
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 整单延时记录（申请延时 + 自主延时，含各轮，每条带 stageOrder），按发生时间倒序。
     */
    @GetMapping("/{demandId}/extend/history")
    public AppApiResponse<List<SysDemandExtendApply>> extendHistoryByDemand(@PathVariable Long demandId)
    {
        try
        {
            return AppApiResponse.ok(demandService.selectExtendHistoryByDemand(demandId, currentUserId()));
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 发布者自主延时：不经申请直接顺延活动轮截止，并同时写入一条延时记录（展示为“自主延时”）。
     * Body: {hours}，前端只填小时；兼容按天数传的旧客户端（按 days*24+hours 折算，须大于 0）
     */
    @PutMapping("/{demandId}/stage/{stageId}/extend")
    public AppApiResponse<SysDemandStage> selfExtend(
            @PathVariable Long demandId,
            @PathVariable Long stageId,
            @RequestBody Map<String, Object> body)
    {
        Long userId = currentUserId();
        int days = intOrZero(body, "days");
        int hours = intOrZero(body, "hours");
        try
        {
            SysDemandStage stage = demandService.selfExtend(demandId, stageId, userId, days, hours);
            stage.setAmount(yuanToFen(stage.getAmount()));
            return AppApiResponse.ok(stage);
        }
        catch (RuntimeException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    /* ===================== helpers ===================== */

    private Long currentUserId()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null)
        {
            throw new RuntimeException("未登录");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof Number) return ((Number) principal).longValue();
        throw new RuntimeException("鉴权异常：principal 非 userId");
    }

    /**
     * 接单侧准入：必须是已登录且已开通创作者资格（authorCapability）的用户。
     *
     * <p>与 content 域 {@code ContentAuthorAccess} 同源：能力由 A 模块每次请求实时查库读取，
     * 资格被后台关闭后，旧 Token 也无法再接单。仅约束接单（押金/抢单）侧，发布者托管不受影响。</p>
     */
    private boolean hasCreatorCapability()
    {
        IdentityContext identity = identityProvider.currentIdentity();
        return identity.isAuthenticated() && identity.isAuthorCapability();
    }

    private static String str(Map<String, Object> map, String key)
    {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Integer intVal(Map<String, Object> map, String key)
    {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    /** 整数取值（缺省/非法一律按 0），用于延时天数/小时。 */
    private static int intOrZero(Map<String, Object> map, String key)
    {
        Integer v = intVal(map, key);
        return v == null ? 0 : v;
    }

    private static Long longVal(Map<String, Object> map, String key)
    {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static BigDecimal decimalVal(Map<String, Object> map, String key)
    {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static BigDecimal yuanToFen(BigDecimal yuan)
    {
        return yuan == null ? null : yuan.multiply(new BigDecimal("100"));
    }

    private static BigDecimal fenToYuan(BigDecimal fen)
    {
        return fen == null ? null : fen.divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
    }

    /** 统一将任务的金额字段（预算/托管额/押金）元→分，供 APP 展示。 */
    private static void applyFen(SysDemand d)
    {
        if (d == null) return;
        d.setBudget(yuanToFen(d.getBudget()));
        d.setEscrowAmount(yuanToFen(d.getEscrowAmount()));
        d.setDepositAmount(yuanToFen(d.getDepositAmount()));
    }

    /** 沙箱返回体中的金额字段元→分（与 APP 以分交互一致）。 */
    private static Map<String, Object> payFen(Map<String, Object> raw)
    {
        Map<String, Object> m = new HashMap<>(raw);
        Object a = m.get("amount");
        if (a instanceof BigDecimal) m.put("amount", yuanToFen((BigDecimal) a));
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<SysDemandStage> parseStages(Object raw)
    {
        if (raw == null) return null;
        if (!(raw instanceof List)) return null;
        List<Map<String, Object>> list = (List<Map<String, Object>>) raw;
        if (list.isEmpty()) return null;
        List<SysDemandStage> result = new ArrayList<>();
        for (Map<String, Object> item : list)
        {
            SysDemandStage s = new SysDemandStage();
            s.setStageOrder(intVal(item, "stageOrder"));
            s.setEpisodeTo(intVal(item, "episodeTo"));
            s.setAmount(decimalVal(item, "amount"));
            s.setDurationDays(intVal(item, "durationDays"));
            s.setDurationHours(intVal(item, "durationHours"));
            result.add(s);
        }
        return result;
    }
}
