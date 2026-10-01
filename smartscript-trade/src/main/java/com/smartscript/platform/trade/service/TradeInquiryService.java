package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.mapper.SysInquiryMapper;

/**
 * C module: inquiry management service.
 * Handles inquiry CRUD, status transitions and inquiry-to-order conversion.
 *
 * 状态枚举（2026-09-29 用户决策收敛）：pending（待回复）/ quoted（议价中）
 * / rejected（已拒绝）/ closed（已关闭）/ deal（已达成）。
 * 原 accepted（已接受）已并入 quoted：卖方接受询盘即进入议价中。
 * 订单不再由询盘手动转出，而是「任意一方接受报价」时直接生成（见 TradeQuoteService.acceptQuote）。
 */
@Service
public class TradeInquiryService
{
    /** 询盘达成（已转订单）状态 */
    public static final String INQUIRY_STATUS_DEAL = "deal";

    /* 询盘状态枚举（2026-09-29 收敛，与前端 tradeEnum.js / 字典 trade_inquiry_status 一致） */
    public static final String INQUIRY_STATUS_PENDING = "pending";
    /** @deprecated 2026-09-29 已并入 {@link #INQUIRY_STATUS_QUOTED}（议价中），仅为兼容存量数据保留 */
    @Deprecated
    public static final String INQUIRY_STATUS_ACCEPTED = "accepted";
    public static final String INQUIRY_STATUS_REJECTED = "rejected";
    public static final String INQUIRY_STATUS_CLOSED = "closed";
    /** 议价中（含原「已接受」与「已报价」两种语义） */
    public static final String INQUIRY_STATUS_QUOTED = "quoted";
    /** 询盘默认有效期（天），PRD 未细化，C 出方案：30 天 */
    private static final int DEFAULT_INQUIRY_VALID_DAYS = 30;

    @Autowired
    private SysInquiryMapper inquiryMapper;

    @Autowired
    private TradeOrderService orderService;

    public List<SysInquiry> selectInquiryList(SysInquiry inquiry)
    {
        return inquiryMapper.selectInquiryList(inquiry);
    }

    public SysInquiry selectInquiryById(Long inquiryId)
    {
        return inquiryMapper.selectInquiryById(inquiryId);
    }

    /**
     * 发起询盘（分工条目 9，PRD APP-TRADE-02）。
     * 买方对某作品发起合作询盘，落库后置 pending（待回复），生成 inquiry_no 与 expire_at。
     * PC 商务运营管理员可代录；APP 侧待 A 的 APP 鉴权合入后复用本方法。
     */
    @Transactional
    public SysInquiry createInquiry(SysInquiry inquiry)
    {
        if (inquiry.getWorkId() == null)
        {
            throw new RuntimeException("作品ID不能为空");
        }
        if (inquiry.getBuyerId() == null)
        {
            throw new RuntimeException("买方ID不能为空");
        }
        if (inquiry.getSellerId() == null)
        {
            throw new RuntimeException("卖方ID不能为空");
        }
        if (inquiry.getLicenseType() == null || inquiry.getLicenseType().isEmpty())
        {
            throw new RuntimeException("授权类型不能为空");
        }
        if (inquiry.getIntendedUse() == null || inquiry.getIntendedUse().isEmpty())
        {
            inquiry.setIntendedUse("-");
        }
        inquiry.setInquiryNo("INQ" + Seq.getId());
        inquiry.setStatus(INQUIRY_STATUS_PENDING);
        if (inquiry.getExpireAt() == null)
        {
            inquiry.setExpireAt(daysFromNow(DEFAULT_INQUIRY_VALID_DAYS));
        }
        inquiry.setCreateBy(SecurityUtils.getUsername());
        inquiry.setCreateTime(new Date());
        inquiryMapper.insertInquiry(inquiry);
        return inquiry;
    }

    /**
     * 接受询盘（分工条目 12）：仅 pending 可接受 → quoted（议价中）。
     * 2026-09-29 用户决策：已接受与已报价统一为议价中，不再落 accepted。
     */
    @Transactional
    public int acceptInquiry(Long inquiryId)
    {
        return transitionInquiry(inquiryId, INQUIRY_STATUS_QUOTED, INQUIRY_STATUS_PENDING);
    }

    /**
     * 拒绝询盘（分工条目 12）：仅 pending 可拒绝 → rejected。
     */
    @Transactional
    public int rejectInquiry(Long inquiryId)
    {
        return transitionInquiry(inquiryId, INQUIRY_STATUS_REJECTED, INQUIRY_STATUS_PENDING);
    }

    /**
     * 关闭询盘（分工条目 12）：pending/quoted/rejected 均可关闭 → closed（deal/closed 不可）。
     * allowedFrom 仍列 accepted 仅为兼容未迁移的存量数据。
     */
    @Transactional
    public int closeInquiry(Long inquiryId)
    {
        return transitionInquiry(inquiryId, INQUIRY_STATUS_CLOSED,
                INQUIRY_STATUS_PENDING, INQUIRY_STATUS_ACCEPTED,
                INQUIRY_STATUS_QUOTED, INQUIRY_STATUS_REJECTED);
    }

    /**
     * P1-08：关闭过期询盘（供 Quartz 定时任务 tradeInquiryTask 调用）。
     * 扫描 status ∈ {pending,accepted,quoted} 且 expire_at < now 的询盘，批量置 closed。
     * @return 关闭条数
     */
    @Transactional
    public int closeExpiredInquiries()
    {
        return inquiryMapper.closeExpiredInquiries();
    }

    /**
     * 询盘状态机：仅当前状态在 allowedFrom 内才允许流转到 target，否则抛错。
     */
    private int transitionInquiry(Long inquiryId, String target, String... allowedFrom)
    {
        SysInquiry inquiry = inquiryMapper.selectInquiryById(inquiryId);
        if (inquiry == null)
        {
            throw new RuntimeException("询盘不存在: " + inquiryId);
        }
        String current = inquiry.getStatus();
        boolean allowed = false;
        for (String from : allowedFrom)
        {
            if (from.equals(current))
            {
                allowed = true;
                break;
            }
        }
        if (!allowed)
        {
            throw new RuntimeException("当前询盘状态不可执行该操作: " + current);
        }
        SysInquiry update = new SysInquiry();
        update.setInquiryId(inquiryId);
        update.setStatus(target);
        update.setUpdateBy(SecurityUtils.getUsername());
        update.setUpdateTime(new Date());
        return inquiryMapper.updateInquiry(update);
    }

    /** 当前时间 + days 天 */
    private Date daysFromNow(int days)
    {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    /**
     * Convert inquiry to order (idempotent).
     *
     * @deprecated 2026-09-29 用户决策：取消「转为订单」功能，订单一律在「任意一方接受报价」时
     * 由 {@link TradeQuoteService#acceptQuote} 生成。本方法仅为兼容接口文档保留，
     * 且收紧为仅 deal（已达成）询盘可调用；前端入口已下线。
     */
    @Deprecated
    @Transactional
    public SysOrder convertToOrder(Long inquiryId)
    {
        SysInquiry inquiry = inquiryMapper.selectInquiryById(inquiryId);
        if (inquiry == null)
        {
            throw new RuntimeException("Inquiry not found: " + inquiryId);
        }
        if (!INQUIRY_STATUS_DEAL.equals(inquiry.getStatus()))
        {
            throw new RuntimeException("仅已达成（接受报价后）的询盘可生成订单，当前状态: " + inquiry.getStatus());
        }

        // 已达成意味着订单已在接受报价时生成，此处幂等返回原单
        return orderService.generateOrderFromInquiry(
                inquiry, null, inquiry.getBudget(), "inquiry", "Inquiry converted to order");
    }
}
