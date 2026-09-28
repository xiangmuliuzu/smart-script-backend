package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.domain.SysBusinessFollow;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.mapper.SysBusinessFollowMapper;
import com.smartscript.platform.trade.mapper.SysInquiryMapper;

/**
 * C module: inquiry management service.
 * Handles inquiry CRUD, follow-up recording and inquiry-to-order conversion.
 *
 * 状态枚举（用户决策 2026-09-28，档案提案）：pending/accepted/rejected/closed/quoted/deal；
 * 转订单后询盘置为 deal（已达成）。
 */
@Service
public class TradeInquiryService
{
    /** 询盘达成（已转订单）状态 */
    public static final String INQUIRY_STATUS_DEAL = "deal";
    /** 询盘跟进记录类型标记（写入 sys_business_follow.follow_type） */
    public static final String FOLLOW_TYPE_INQUIRY = "inquiry";

    /* 询盘状态枚举（已定稿，与前端 tradeEnum.js / 字典 trade_inquiry_status 一致） */
    public static final String INQUIRY_STATUS_PENDING = "pending";
    public static final String INQUIRY_STATUS_ACCEPTED = "accepted";
    public static final String INQUIRY_STATUS_REJECTED = "rejected";
    public static final String INQUIRY_STATUS_CLOSED = "closed";
    public static final String INQUIRY_STATUS_QUOTED = "quoted";
    /** 询盘默认有效期（天），PRD 未细化，C 出方案：30 天 */
    private static final int DEFAULT_INQUIRY_VALID_DAYS = 30;

    @Autowired
    private SysInquiryMapper inquiryMapper;

    @Autowired
    private SysBusinessFollowMapper followMapper;

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
     * 接受询盘（分工条目 12）：仅 pending 可接受 → accepted。
     */
    @Transactional
    public int acceptInquiry(Long inquiryId)
    {
        return transitionInquiry(inquiryId, INQUIRY_STATUS_ACCEPTED, INQUIRY_STATUS_PENDING);
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
     * 关闭询盘（分工条目 12）：pending/accepted/quoted/rejected 均可关闭 → closed（deal/closed 不可）。
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
     * 记录询盘跟进：落一条 sys_business_follow（follow_type=inquiry），并把跟进内容追加到询盘 remark。
     * 修复原空壳实现（P0-03）。
     */
    @Transactional
    public int followUp(Long inquiryId, String content)
    {
        SysInquiry inquiry = inquiryMapper.selectInquiryById(inquiryId);
        if (inquiry == null)
        {
            throw new RuntimeException("询盘不存在: " + inquiryId);
        }

        SysBusinessFollow follow = new SysBusinessFollow();
        follow.setPartnerId(null);
        follow.setFollowerId(SecurityUtils.getUserId());
        follow.setFollowType(FOLLOW_TYPE_INQUIRY);
        follow.setContent(content);
        follow.setStatus("ongoing");
        follow.setFollowTime(new Date());
        follow.setCreateBy(SecurityUtils.getUsername());
        follow.setCreateTime(new Date());
        follow.setRemark("询盘 " + inquiry.getInquiryNo() + " 跟进");
        followMapper.insertFollow(follow);

        // 同步把最新跟进追加到询盘备注，便于详情展示
        SysInquiry update = new SysInquiry();
        update.setInquiryId(inquiryId);
        String oldRemark = inquiry.getRemark() == null ? "" : inquiry.getRemark() + "\n";
        update.setRemark(oldRemark + "[" + SecurityUtils.getUsername() + "] " + content);
        update.setUpdateBy(SecurityUtils.getUsername());
        update.setUpdateTime(new Date());
        return inquiryMapper.updateInquiry(update);
    }

    /**
     * Convert inquiry to order (idempotent). 订单生成委托 TradeOrderService，询盘置 deal。
     */
    @Transactional
    public SysOrder convertToOrder(Long inquiryId)
    {
        SysInquiry inquiry = inquiryMapper.selectInquiryById(inquiryId);
        if (inquiry == null)
        {
            throw new RuntimeException("Inquiry not found: " + inquiryId);
        }

        SysOrder order = orderService.generateOrderFromInquiry(
                inquiry, null, inquiry.getBudget(), "inquiry", "Inquiry converted to order");

        // 幂等：仅当询盘尚未达成时才更新状态，避免重复转订单覆盖
        if (!INQUIRY_STATUS_DEAL.equals(inquiry.getStatus()))
        {
            SysInquiry update = new SysInquiry();
            update.setInquiryId(inquiryId);
            update.setStatus(INQUIRY_STATUS_DEAL);
            update.setUpdateBy(SecurityUtils.getUsername());
            update.setUpdateTime(new Date());
            inquiryMapper.updateInquiry(update);
        }

        return order;
    }
}
