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

import com.smartscript.platform.content.domain.SysBanner;
import com.smartscript.platform.content.domain.SysPromotionApplication;
import com.smartscript.platform.content.domain.SysPromotionSlot;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.dto.PromotionApplyRequest;
import com.smartscript.platform.content.dto.PromotionGrantRequest;
import com.smartscript.platform.content.dto.PromotionOverviewVo;
import com.smartscript.platform.content.dto.PromotionPriceRequest;
import com.smartscript.platform.content.dto.PromotionSlotPageVo;
import com.smartscript.platform.content.dto.PromotionSlotVo;
import com.smartscript.platform.content.mapper.SysBannerMapper;
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

    @Autowired
    private SysBannerMapper bannerMapper;

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

        List<SysBanner> banners = new ArrayList<>();
        if ("banner".equals(slotType))
        {
            SysBanner bannerQuery = new SysBanner();
            bannerQuery.setPosition("home_top");
            banners = bannerMapper.selectBannerList(bannerQuery);
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
            for (SysPromotionApplication app : bySlot.getOrDefault(slot.getSlotNo(), Collections.emptyList()))
            {
                if (STATUS_PAID.equals(app.getStatus()))
                {
                    if (!now.before(app.getStartTime()) && !now.after(app.getEndTime()))
                    {
                        if (active == null || app.getStartTime().after(active.getStartTime()))
                        {
                            active = app;
                        }
                    }
                    else if (app.getStartTime().after(now))
                    {
                        if (future == null || app.getStartTime().before(future.getStartTime()))
                        {
                            future = app;
                        }
                    }
                }
                else if (STATUS_APPROVED.equals(app.getStatus()) && app.getLockExpireAt() != null
                        && app.getLockExpireAt().after(now))
                {
                    locked = app;
                }
            }

            decorate(active);
            decorate(locked);
            decorate(future);

            SysBanner banner = null;
            SysPromotionApplication current = active != null ? active : locked != null ? locked : future;
            if ("banner".equals(slotType))
            {
                if (current != null && current.getBannerId() != null)
                {
                    banner = bannerMapper.selectBannerById(current.getBannerId());
                }
                // 申请单自带素材时预览以 current.materialUrl 为准，避免回退到同位次的运营 Banner
                boolean hasOwnMaterial = current != null && current.getBannerId() == null
                        && current.getMaterialUrl() != null && !current.getMaterialUrl().trim().isEmpty();
                if (banner == null && !hasOwnMaterial)
                {
                    for (SysBanner item : banners)
                    {
                        if (item.getSortOrder() != null && item.getSortOrder().equals(slot.getSlotNo()))
                        {
                            banner = item;
                            break;
                        }
                    }
                }
                vo.setBanner(banner);
            }

            vo.setCurrent(current);
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
            else if (banner != null)
            {
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

            if ("banner".equals(slotType) && active != null && banner == null
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
            if (STATUS_PAID.equals(app.getStatus()) && app.getStartTime().after(now)
                    && app.getStartTime().getTime() <= now.getTime() + TimeUnit.DAYS.toMillis(7))
            {
                decorate(app);
                upcoming.add(app);
            }
        }
        upcoming.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));

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
            return applicationMapper.updateAudit(update);
        }

        // 锁坑：先锁 slot 行，再校验时间窗冲突
        lockSlot(app.getSlotType(), app.getSlotNo());
        assertNoConflict(app.getSlotType(), app.getSlotNo(), app.getStartTime(), app.getEndTime(), applyId);

        update.setStatus(STATUS_APPROVED);
        update.setPayStatus("unpaid");
        update.setLockExpireAt(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(LOCK_HOURS)));
        return applicationMapper.updateAudit(update);
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

        lockSlot(slot.getSlotType(), slot.getSlotNo());
        assertNoConflict(slot.getSlotType(), slot.getSlotNo(), start, end, null);

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
        return applicationMapper.cancel(applyId, operator);
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

    @Override
    public int bindBanner(Long applyId, Long bannerId, String operator)
    {
        SysPromotionApplication app = applicationMapper.selectApplicationById(applyId);
        if (app == null)
        {
            throw new IllegalArgumentException("推广申请不存在");
        }
        if (!"banner".equals(app.getSlotType()))
        {
            throw new IllegalStateException("仅轮播位申请可绑定素材");
        }
        return applicationMapper.updateBanner(applyId, bannerId);
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

        assertNoConflict(slot.getSlotType(), slot.getSlotNo(), start, end, null);

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

    private void assertNoConflict(String slotType, Integer slotNo, Date start, Date end, Long excludeId)
    {
        int conflicts = applicationMapper.countConflicts(slotType, slotNo, start, end, new Date(), excludeId);
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
