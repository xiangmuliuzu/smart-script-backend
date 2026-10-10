package com.smartscript.platform.trade.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.trade.domain.SysWork;
import com.smartscript.platform.trade.mapper.SysWorkMapper;

/**
 * C module: trade work management service.
 * Handles listing trade works and updating trade settings (price, license type, etc.).
 */
@Service
public class TradeWorkService
{
    /**
     * 已退役的授权类型值（2026-09-30 用户决策）。
     * 「可议价」不是一种授权类型，而是所有作品共有的能力，改由 negotiable_min/max 表达区间；
     * 依据分工第 4 条「授权类型、授权价格和可议价范围」，且附件6.1 从未将 negotiable 列为授权类型取值。
     */
    private static final String LICENSE_TYPE_NEGOTIABLE_RETIRED = "negotiable";

    @Autowired
    private SysWorkMapper workMapper;

    public List<SysWork> selectTradeWorkList(SysWork work)
    {
        return workMapper.selectTradeWorkList(work);
    }

    public SysWork selectWorkById(Long workId)
    {
        return workMapper.selectWorkById(workId);
    }

    /**
     * Update trade settings for a work. Requires the work to be already reviewed/approved.
     */
    public int updateTradeSettings(SysWork work)
    {
        assertTradeTypeNotRetired(work);
        assertNegotiableRange(work);
        work.setUpdateBy(SecurityUtils.getUsername());
        work.setUpdateTime(new Date());
        return workMapper.updateTradeSettings(work);
    }

    /**
     * 授权类型校验：拒绝写入已退役的 negotiable，避免前端绕过后产生新的脏枚举值。
     */
    private void assertTradeTypeNotRetired(SysWork work)
    {
        if (LICENSE_TYPE_NEGOTIABLE_RETIRED.equals(work.getTradeType()))
        {
            throw new RuntimeException("授权类型「可议价」已下线：所有作品均可议价，"
                    + "请改选独家/非独家/改编，并用议价上下限设定区间");
        }
    }

    /**
     * 议价区间校验（2026-09-30 决策：区间与授权类型解绑，所有作品均可议价）：
     * 上下限均可为空表示不限，也允许只填单边；但不得为负，且两者都填时下限不得大于上限。
     * 与前端 TradeWorks.vue validateRange 同口径。
     */
    private void assertNegotiableRange(SysWork work)
    {
        BigDecimal min = work.getNegotiableMin();
        BigDecimal max = work.getNegotiableMax();
        if (min != null && min.signum() < 0)
        {
            throw new RuntimeException("议价下限不能为负数");
        }
        if (max != null && max.signum() < 0)
        {
            throw new RuntimeException("议价上限不能为负数");
        }
        if (min != null && max != null && min.compareTo(max) > 0)
        {
            throw new RuntimeException("议价下限不得大于上限（当前 " + min.toPlainString()
                    + " > " + max.toPlainString() + "）");
        }
    }

    /**
     * 上架作品到交易大厅（分工条目：2.26 POST /trade/works）。
     *
     * 审核前置口径（2026-09-29 修正）：内容侧作品状态实测为 on_shelf（已上架）/ off_shelf（已下架），
     * 附件5.1 触发器设计的 approved/rejected/returned 因审核回写链路（T01 触发器）未落地而不会出现，
     * 原实现硬要求 status='approved' 会导致上架 100% 失败（TRADE_WORK_001）。
     * 现放宽为白名单 {approved, on_shelf}：只要作品在内容侧处于已上架（或历史 approved）即可发布交易；
     * off_shelf 提示先回内容侧上架，其余状态提示审核未通过。
     */
    public int enableTrade(SysWork work)
    {
        if (work == null || work.getWorkId() == null)
        {
            throw new RuntimeException("作品ID不能为空");
        }
        SysWork existing = workMapper.selectWorkById(work.getWorkId());
        if (existing == null)
        {
            throw new RuntimeException("作品不存在: " + work.getWorkId());
        }
        String status = existing.getStatus();
        boolean listable = "approved".equals(status) || "on_shelf".equals(status);
        if (!listable)
        {
            if ("off_shelf".equals(status))
            {
                throw new RuntimeException("作品已在内容侧下架，请先上架后再发布到交易");
            }
            throw new RuntimeException("仅审核通过（已上架）的作品可发布到交易，当前状态: " + status);
        }
        work.setTradeEnabled(1);
        return updateTradeSettings(work);
    }
}
