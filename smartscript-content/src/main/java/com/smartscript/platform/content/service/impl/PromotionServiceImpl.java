package com.smartscript.platform.content.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartscript.platform.content.domain.SysPromotionApplication;
import com.smartscript.platform.content.domain.SysPromotionSlot;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.dto.PromotionApplyRequest;
import com.smartscript.platform.content.dto.PromotionGrantRequest;
import com.smartscript.platform.content.dto.PromotionOverviewVo;
import com.smartscript.platform.content.dto.PromotionPriceRequest;
import com.smartscript.platform.content.dto.PromotionSlotPageVo;
import com.smartscript.platform.content.dto.PromotionSlotVo;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysPromotionApplicationMapper;
import com.smartscript.platform.content.mapper.SysPromotionSlotMapper;
import com.smartscript.platform.content.service.IPromotionService;

/**
 * 作品推广服务实现
 *
 * 关键口径：
 * 1. 坑位占用以「时间窗」判定：同一 slot_type + slot_no 的 paid 记录不可重叠；
 *    审核通过后锁坑 48 小时（approved + unpaid），超时自动释放（派生，不依赖定时任务）。
 * 2. 审核/免费直上在同一事务内 SELECT ... FOR UPDATE 锁 slot 行，再校验冲突，避免并发超卖。
 * 3. 投放中 / 待生效 / 已结束 全部由时间窗派生，不额外维护状态列。
 *
 * @author xiangsipeng
 */
@Service
public class PromotionServiceImpl implements IPromotionService
{
    private static final Set<String> SLOT_TYPES = new HashSet<>(Arrays.asList("banner", "search", "hot"));

    private static final Set<Integer> DAY_OPTIONS = new HashSet<>(Arrays.asList(7, 15, 30));

    /** 审核通过后锁坑时长（小时） */
    private static final long LOCK_HOURS = 48L;

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_PAID = "paid";
    private static final String STATUS_CANCELLED = "cancelled";

    @Autowired
    private SysPromotionSlotMapper slotMapper;

    @Autowired
    private SysPromotionApplicationMapper applicationMapper;

    @Autowired
    private SysContentWorkMapper workMapper;

    // ==================== 坑位总览 ====================

    @Override
    public PromotionSlotPageVo getSlotOverview(String slotType)
    {
        return buildSlotPage(slotType, true);
    }

    @Override
    public PromotionSlotPageVo getAppSlots(String slotType)
    {
        return buildSlotPage(slotType, false);
    }

    private PromotionSlotPageVo buildSlotPage(String slotType, boolean admin)
    {
        requireSlotType(slotType);
        Date now = new Date();
        List<SysPromotionSlot> slots = slotMapper.selectSlots(slotType);
        List<SysPromotionApplication> open = applicationMapper.selectOpenOccupancies(slotType, now);

        Map<Integer, List<SysPromotionApplication>> bySlot = new HashMap<>();
        for (SysPromotionApplication app : open)
        {
            bySlot.computeIfAbsent(app.getSlotNo(), key -> new ArrayList<>()).add(app);
        }

        List<PromotionSlotVo> result = new ArrayList<>();
        int activeCount = 0;
        int lockedCount = 0;
        int idleCount = 0;

        for (SysPromotionSlot slot : slots)
        {
            PromotionSlotVo vo = new PromotionSlotVo();
            vo.setSlotId(slot.getSlotId());
            vo.setSlotType(slot.getSlotType());
            vo.setSlotNo(slot.getSlotNo());
            vo.setPricePerDay(slot.getPricePerDay());
            vo.setEnabled(slot.getEnabled());
            vo.setRemark(slot.getRemark());

            SysPromotionApplication active = null;
            SysPromotionApplication locked = null;
            SysPromotionApplication future = null;
            // 运营兜底素材：exclusive=0，可被付费素材覆盖，不计入占用与预约冲突
            SysPromotionApplication backup = null;
            for (SysPromotionApplication app : bySlot.getOrDefault(slot.getSlotNo(), Collections.emptyList()))
            {
                boolean exclusive = app.getExclusive() == null || app.getExclusive() != 0;
                if (STATUS_PAID.equals(app.getStatus()))
                {
                    if (!now.before(app.getStartTime()) && !now.after(app.getEndTime()))
                    {
                        if (exclusive)
                        {
                            if (active == null || app.getStartTime().after(active.getStartTime()))
                            {
                                active = app;
                            }
                        }
                        else if (backup == null || app.getStartTime().after(backup.getStartTime()))
                        {
                            backup = app;
                        }
                    }
                    else if (exclusive && app.getStartTime().after(now))
                    {
                        if (future == null || app.getStartTime().before(future.getStartTime()))
                        {
                            future = app;
                        }
                    }
                }
                else if (exclusive && STATUS_APPROVED.equals(app.getStatus()) && app.getLockExpireAt() != null
                        && app.getLockExpireAt().after(now))
                {
                    locked = app;
                }
            }

            decorate(active);
            decorate(locked);
            decorate(future);
            decorate(backup);

            // current 只放锁定单，App 端据此判断是否可被预约；兜底素材走 backup
            vo.setCurrent(active != null ? active : locked != null ? locked : future);
            vo.setBackup(backup);
            if (active != null)
            {
                vo.setState("active");
                activeCount++;
            }
            else if (locked != null)
            {
                vo.setState("locked");
                lockedCount++;
            }
            else if (future != null)
            {
                vo.setState("reserved");
            }
            else if (backup != null)
            {
                // 只有运营兜底素材在展示：对 App 而言等价「可覆盖」，仍可被预约
                vo.setState("active");
                activeCount++;
            }
            else if ("0".equals(slot.getEnabled()))
            {
                vo.setState("disabled");
                idleCount++;
            }
            else
            {
                vo.setState("idle");
                idleCount++;
            }

            if ("banner".equals(slotType) && active != null && active.getBannerId() == null
                    && (active.getMaterialUrl() == null || active.getMaterialUrl().trim().isEmpty()))
            {
                vo.setState("missing_material");
            }

            result.add(vo);
        }

        // 下一笔预约：取每个坑位最近的一笔「未开始」或「待支付锁坑」之外的已支付预约
        List<SysPromotionApplication> upcoming = new ArrayList<>();
        for (SysPromotionApplication app : open)
        {
            // 运营兜底素材不算「预约」，不进入接下来 7 天的排期提示
            boolean exclusive = app.getExclusive() == null || app.getExclusive() != 0;
            if (exclusive && STATUS_PAID.equals(app.getStatus()) && app.getStartTime().after(now)
                    && app.getStartTime().getTime() <= now.getTime() + TimeUnit.DAYS.toMillis(7))
            {
                decorate(app);
                upcoming.add(app);
            }
        }
        upcoming.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));

        // App 端只看到位次占用情况，隐藏他人申请单的价格、申请号、作者身份等敏感字段
        if (!admin)
        {
            for (PromotionSlotVo vo : result)
            {
                maskForApp(vo.getCurrent());
                maskForApp(vo.getBackup());
            }
            for (SysPromotionApplication app : upcoming)
            {
                maskForApp(app);
            }
        }

        PromotionOverviewVo overview = new PromotionOverviewVo();
        overview.setSlotType(slotType);
        overview.setTotalSlots(slots.size());
        overview.setActiveCount(activeCount);
        overview.setLockedCount(lockedCount);
        overview.setIdleCount(idleCount);
        overview.setPendingCount(applicationMapper.countPending(slotType));
        if (admin)
        {
            BigDecimal revenue = applicationMapper.selectMonthRevenue(slotType);
            overview.setMonthRevenue(revenue == null ? BigDecimal.ZERO : revenue);
        }

        PromotionSlotPageVo page = new PromotionSlotPageVo();
        page.setOverview(overview);
        page.setSlots(result);
        page.setUpcoming(upcoming);
        return page;
    }

    /** App 端位次占用信息脱敏：仅保留展示所需的作品与时间字段。 */
    private void maskForApp(SysPromotionApplication app)
    {
        if (app == null)
        {
            return;
        }
        app.setApplyNo(null);
        app.setAuthorId(null);
        app.setAuthorName(null);
        app.setAmount(null);
        app.setAuditBy(null);
        app.setRejectReason(null);
        app.setRemark(null);
        app.setPayTime(null);
        app.setBannerId(null);
        app.setCreateBy(null);
        app.setUpdateBy(null);
    }

    // ==================== 申请列表 / 详情 ====================

    @Override
    public List<SysPromotionApplication> selectApplicationList(SysPromotionApplication query)
    {
        List<SysPromotionApplication> list = applicationMapper.selectApplicationList(query);
        for (SysPromotionApplication app : list)
        {
            decorate(app);
        }
        return list;
    }

    @Override
    public SysPromotionApplication selectApplicationById(Long applyId)
    {
        return decorate(applicationMapper.selectApplicationById(applyId));
    }

    // ==================== 审核 / 免费直上 / 下架 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int audit(Long applyId, boolean approved, String rejectReason, String operator)
    {
        SysPromotionApplication app = applicationMapper.selectApplicationById(applyId);
        if (app == null)
        {
            throw new IllegalArgumentException("推广申请不存在");
        }
        if (!STATUS_PENDING.equals(app.getStatus()))
        {
            throw new IllegalStateException("仅待审核申请可审核");
        }

        SysPromotionApplication update = new SysPromotionApplication();
        update.setApplyId(applyId);
        update.setAuditBy(operator);

        if (!approved)
        {
            update.setStatus(STATUS_REJECTED);
            update.setRejectReason(rejectReason == null || rejectReason.isBlank() ? "不符合推广要求" : rejectReason);
            int rows = applicationMapper.updateAudit(update);
            requireUpdated(rows);
            return rows;
        }

        // 锁坑：先锁 slot 行，再校验时间窗冲突
        lockSlot(app.getSlotType(), app.getSlotNo());
        assertNoConflict(app.getSlotType(), app.getSlotNo(), app.getStartTime(), app.getEndTime(), applyId,
                app.getExclusive() == null ? 1 : app.getExclusive());

        update.setStatus(STATUS_APPROVED);
        update.setPayStatus("unpaid");
        update.setLockExpireAt(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(LOCK_HOURS)));
        int rows = applicationMapper.updateAudit(update);
        requireUpdated(rows);
        return rows;
    }

    /** 审核更新未命中（并发重复审核 / 用户已取消）时统一提示刷新。 */
    private void requireUpdated(int rows)
    {
        if (rows <= 0)
        {
            throw new IllegalStateException("申请状态已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long grant(PromotionGrantRequest request, String operator)
    {
        if (request == null || request.getWorkId() == null)
        {
            throw new IllegalArgumentException("请选择作品");
        }
        requireSlotType(request.getSlotType());
        int days = request.getDays() == null ? 7 : request.getDays();
        if (days < 1 || days > 365)
        {
            throw new IllegalArgumentException("投放天数需在 1~365 之间");
        }
        SysWork work = workMapper.selectWorkById(request.getWorkId());
        if (work == null || work.getIsDeleted() != null && "1".equals(work.getIsDeleted()))
        {
            throw new IllegalArgumentException("作品不存在");
        }
        if (!"on_shelf".equals(work.getStatus()))
        {
            throw new IllegalStateException("仅已上架作品可上推荐");
        }
        SysPromotionSlot slot = requireEnabledSlot(request.getSlotType(), request.getSlotNo());

        Date start = request.getStartTime() == null ? new Date() : request.getStartTime();
        if (start.before(new Date()))
        {
            start = new Date();
        }
        Date end = new Date(start.getTime() + TimeUnit.DAYS.toMillis(days));
        // 锁定：独占时间窗，App 不可预约；不锁定：运营兜底素材，可被付费素材覆盖
        int exclusive = request.getExclusive() != null && request.getExclusive() == 0 ? 0 : 1;

        lockSlot(slot.getSlotType(), slot.getSlotNo());
        assertNoConflict(slot.getSlotType(), slot.getSlotNo(), start, end, null, exclusive);

        SysPromotionApplication app = new SysPromotionApplication();
        app.setApplyNo(nextApplyNo("GF"));
        app.setWorkId(work.getWorkId());
        app.setAuthorId(work.getAuthorId());
        app.setSlotType(slot.getSlotType());
        app.setSlotNo(slot.getSlotNo());
        app.setStartTime(start);
        app.setEndTime(end);
        app.setDays(days);
        app.setAmount(BigDecimal.ZERO);
        if ("banner".equals(slot.getSlotType()))
        {
            String material = request.getMaterialUrl() == null ? null : request.getMaterialUrl().trim();
            app.setMaterialUrl(material == null || material.isEmpty() ? work.getCover() : material);
        }
        app.setSource("admin_grant");
        app.setExclusive(exclusive);
        app.setStatus(STATUS_PAID);
        app.setPayStatus("paid");
        app.setPayTime(new Date());
        app.setAuditBy(operator);
        app.setAuditTime(new Date());
        app.setRemark("管理员免费上推荐");
        app.setCreateBy(operator);
        applicationMapper.insertApplication(app);
        return app.getApplyId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int offline(Long applyId, String operator)
    {
        SysPromotionApplication app = applicationMapper.selectApplicationById(applyId);
        if (app == null)
        {
            throw new IllegalArgumentException("推广申请不存在");
        }
        if (STATUS_CANCELLED.equals(app.getStatus()) || STATUS_REJECTED.equals(app.getStatus()))
        {
            throw new IllegalStateException("该申请已结束");
        }
        int rows = applicationMapper.cancel(applyId, operator);
        requireUpdated(rows);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateExclusive(Long applyId, Integer exclusive, String operator)
    {
        SysPromotionApplication app = applicationMapper.selectApplicationById(applyId);
        if (app == null)
        {
            throw new IllegalArgumentException("推广记录不存在");
        }
        if (!"admin_grant".equals(app.getSource()))
        {
            throw new IllegalStateException("仅管理员上架的记录可切换锁定状态");
        }
        if (!STATUS_PAID.equals(app.getStatus()))
        {
            throw new IllegalStateException("仅投放中的记录可切换锁定状态");
        }
        if (app.getEndTime() != null && app.getEndTime().before(new Date()))
        {
            throw new IllegalStateException("已结束的记录不可切换锁定状态");
        }
        int target = exclusive != null && exclusive == 0 ? 0 : 1;
        // 改为锁定单时，需要确认该时段没有其他锁定单；改为兜底单时同理不能与其他兜底单重叠
        lockSlot(app.getSlotType(), app.getSlotNo());
        assertNoConflict(app.getSlotType(), app.getSlotNo(), app.getStartTime(), app.getEndTime(), applyId,
                target);
        int rows = applicationMapper.updateExclusive(applyId, target, operator);
        requireUpdated(rows);
        return rows;
    }

    // ==================== 价格 / 素材 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrices(PromotionPriceRequest request)
    {
        if (request == null || request.getSlots() == null || request.getSlots().isEmpty())
        {
            throw new IllegalArgumentException("请提供需要修改的位次");
        }
        int rows = 0;
        for (PromotionPriceRequest.Item item : request.getSlots())
        {
            if (item.getSlotId() == null)
            {
                continue;
            }
            if (item.getPricePerDay() != null && item.getPricePerDay().signum() < 0)
            {
                throw new IllegalArgumentException("单价不能为负数");
            }
            SysPromotionSlot slot = new SysPromotionSlot();
            slot.setSlotId(item.getSlotId());
            slot.setPricePerDay(item.getPricePerDay());
            slot.setEnabled(item.getEnabled());
            rows += slotMapper.updatePrice(slot);
        }
        return rows;
    }

    // ==================== App 端 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(PromotionApplyRequest request, Long authorId)
    {
        if (request == null || request.getWorkId() == null)
        {
            throw new IllegalArgumentException("请选择作品");
        }
        requireSlotType(request.getSlotType());
        if (request.getDays() == null || !DAY_OPTIONS.contains(request.getDays()))
        {
            throw new IllegalArgumentException("投放时长仅支持 7 / 15 / 30 天");
        }
        SysWork work = workMapper.selectOwnedWork(request.getWorkId(), authorId);
        if (work == null)
        {
            throw new IllegalArgumentException("作品不存在或不属于你");
        }
        if (!"on_shelf".equals(work.getStatus()))
        {
            throw new IllegalStateException("仅已上架作品可申请推广");
        }
        SysPromotionSlot slot = requireEnabledSlot(request.getSlotType(), request.getSlotNo());

        String materialUrl = null;
        if ("banner".equals(slot.getSlotType()))
        {
            materialUrl = request.getMaterialUrl() == null ? null : request.getMaterialUrl().trim();
            if (materialUrl == null || materialUrl.isEmpty())
            {
                throw new IllegalArgumentException("请先上传轮播素材");
            }
            if (materialUrl.length() > 500)
            {
                throw new IllegalArgumentException("轮播素材地址过长");
            }
        }

        Date start = request.getStartTime() == null ? new Date() : request.getStartTime();
        if (start.before(new Date()))
        {
            start = new Date();
        }
        Date end = new Date(start.getTime() + TimeUnit.DAYS.toMillis(request.getDays()));

        assertNoConflict(slot.getSlotType(), slot.getSlotNo(), start, end, null, 1);

        SysPromotionApplication app = new SysPromotionApplication();
        app.setApplyNo(nextApplyNo("PR"));
        app.setWorkId(work.getWorkId());
        app.setAuthorId(authorId);
        app.setSlotType(slot.getSlotType());
        app.setSlotNo(slot.getSlotNo());
        app.setStartTime(start);
        app.setEndTime(end);
        app.setDays(request.getDays());
        app.setAmount(slot.getPricePerDay().multiply(BigDecimal.valueOf(request.getDays())));
        app.setMaterialUrl(materialUrl);
        app.setSource("author_paid");
        app.setExclusive(1);
        app.setStatus(STATUS_PENDING);
        app.setPayStatus("unpaid");
        app.setCreateBy(String.valueOf(authorId));
        applicationMapper.insertApplication(app);
        return app.getApplyId();
    }

    @Override
    public List<SysPromotionApplication> selectMyApplications(Long authorId, String viewStatus)
    {
        SysPromotionApplication query = new SysPromotionApplication();
        query.setAuthorId(authorId);
        query.setViewStatus(viewStatus);
        return selectApplicationList(query);
    }

    @Override
    public SysPromotionApplication selectMyApplication(Long applyId, Long authorId)
    {
        SysPromotionApplication app = applicationMapper.selectApplicationById(applyId);
        if (app == null || authorId == null || !authorId.equals(app.getAuthorId()))
        {
            return null;
        }
        return decorate(app);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancel(Long applyId, Long authorId)
    {
        SysPromotionApplication app = applicationMapper.selectApplicationById(applyId);
        if (app == null || authorId == null || !authorId.equals(app.getAuthorId()))
        {
            throw new IllegalArgumentException("推广申请不存在");
        }
        if (!STATUS_PENDING.equals(app.getStatus())
                && !(STATUS_APPROVED.equals(app.getStatus()) && "unpaid".equals(app.getPayStatus())))
        {
            throw new IllegalStateException("仅待审核或待支付申请可取消");
        }
        return applicationMapper.cancel(applyId, String.valueOf(authorId));
    }

    // ==================== 内部工具 ====================

    /** 派生状态文案 + 锁坑剩余秒数；入参可为 null */
    private SysPromotionApplication decorate(SysPromotionApplication app)
    {
        if (app == null)
        {
            return null;
        }
        Date now = new Date();
        String label;
        if (STATUS_PENDING.equals(app.getStatus()))
        {
            label = "待审核";
            app.setStatusLabel(label);
        }
        else if (STATUS_REJECTED.equals(app.getStatus()))
        {
            label = "已驳回";
            app.setStatusLabel(label);
        }
        else if (STATUS_CANCELLED.equals(app.getStatus()))
        {
            label = "已取消";
            app.setStatusLabel(label);
        }
        else if (STATUS_APPROVED.equals(app.getStatus()) && "unpaid".equals(app.getPayStatus()))
        {
            if (app.getLockExpireAt() == null || app.getLockExpireAt().before(now))
            {
                label = "已失效";
            }
            else
            {
                label = "待支付";
                app.setLockRemainSeconds(
                        Math.max(0L, (app.getLockExpireAt().getTime() - now.getTime()) / 1000L));
            }
            app.setStatusLabel(label);
        }
        else
        {
            if (app.getStartTime() != null && app.getStartTime().after(now))
            {
                label = "待生效";
            }
            else if (app.getEndTime() != null && app.getEndTime().before(now))
            {
                label = "已结束";
            }
            else
            {
                label = "投放中";
            }
            app.setStatusLabel(label);
        }
        return app;
    }

    private void requireSlotType(String slotType)
    {
        if (slotType == null || !SLOT_TYPES.contains(slotType))
        {
            throw new IllegalArgumentException("资源位类型不合法");
        }
    }

    private SysPromotionSlot requireEnabledSlot(String slotType, Integer slotNo)
    {
        if (slotNo == null)
        {
            throw new IllegalArgumentException("请选择位次");
        }
        SysPromotionSlot slot = slotMapper.selectSlotByNo(slotType, slotNo);
        if (slot == null)
        {
            throw new IllegalArgumentException("坑位不存在");
        }
        if ("0".equals(slot.getEnabled()))
        {
            throw new IllegalStateException("该坑位已暂停售卖");
        }
        return slot;
    }

    private void lockSlot(String slotType, Integer slotNo)
    {
        SysPromotionSlot slot = slotMapper.selectSlotByNoForUpdate(slotType, slotNo);
        if (slot == null)
        {
            throw new IllegalArgumentException("坑位不存在");
        }
    }

    private void assertNoConflict(String slotType, Integer slotNo, Date start, Date end, Long excludeId,
            int exclusive)
    {
        int conflicts = applicationMapper.countConflicts(slotType, slotNo, start, end, new Date(), excludeId,
                exclusive);
        if (conflicts > 0)
        {
            throw new IllegalStateException("该位次在所选时段内已被占用，请调整时段或位次");
        }
    }

    private String nextApplyNo(String prefix)
    {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(100, 1000);
        return prefix + time + random;
    }
}
