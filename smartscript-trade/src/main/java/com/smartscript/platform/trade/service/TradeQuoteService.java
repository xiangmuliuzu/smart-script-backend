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
import com.smartscript.platform.trade.domain.SysQuote;
import com.smartscript.platform.trade.mapper.SysInquiryMapper;
import com.smartscript.platform.trade.mapper.SysQuoteMapper;

/**
 * C module: quote/negotiation service.
 *
 * 报价状态枚举（用户决策 2026-09-28，档案提案）：pending/accepted/rejected/expired。
 * 状态机：仅 pending 可被接受/拒绝；接受报价即生成订单（幂等，委托 TradeOrderService）。
 * 过期报价（expireAt 早于当前时间）不可接受。
 */
@Service
public class TradeQuoteService
{
    public static final String QUOTE_PENDING = "pending";
    public static final String QUOTE_ACCEPTED = "accepted";
    public static final String QUOTE_REJECTED = "rejected";
    public static final String QUOTE_EXPIRED = "expired";

    /* 报价方角色（sys_quote.quoter_role）：卖方报价 / 买方议价 */
    public static final String QUOTER_ROLE_SELLER = "seller";
    public static final String QUOTER_ROLE_BUYER = "buyer";
    /** 报价默认有效天数（DDL valid_days NOT NULL），PRD 未细化，C 出方案：7 天 */
    private static final int DEFAULT_QUOTE_VALID_DAYS = 7;

    @Autowired
    private SysQuoteMapper quoteMapper;

    @Autowired
    private SysInquiryMapper inquiryMapper;

    @Autowired
    private TradeOrderService orderService;

    public List<SysQuote> selectQuoteList(SysQuote quote)
    {
        return quoteMapper.selectQuoteList(quote);
    }

    public SysQuote selectQuoteById(Long quoteId)
    {
        return quoteMapper.selectQuoteById(quoteId);
    }

    public int insertQuote(SysQuote quote)
    {
        return quoteMapper.insertQuote(quote);
    }

    /**
     * 提交报价（分工条目 17，PRD APP-TRADE-03）：卖方对询盘报价，quoter_role=seller。
     */
    @Transactional
    public SysQuote createQuote(SysQuote quote)
    {
        return submitQuote(quote, QUOTER_ROLE_SELLER);
    }

    /**
     * 买方议价（分工条目 19，PRD「报价变更保留记录」）：
     * 以新增一行 quoter_role=buyer 的报价保留历史，不改写卖方原报价行。
     */
    @Transactional
    public SysQuote counterOffer(SysQuote quote)
    {
        return submitQuote(quote, QUOTER_ROLE_BUYER);
    }

    /**
     * 报价/议价入库公用逻辑：校验询盘可报价 → 补齐 NOT NULL 字段（DDL 约束）
     * → 生成 quote_no/expire_at → insert → 询盘置 quoted。
     */
    private SysQuote submitQuote(SysQuote quote, String forcedRole)
    {
        if (quote.getInquiryId() == null)
        {
            throw new RuntimeException("关联询盘ID不能为空");
        }
        if (quote.getPrice() == null)
        {
            throw new RuntimeException("报价金额不能为空");
        }
        SysInquiry inquiry = inquiryMapper.selectInquiryById(quote.getInquiryId());
        if (inquiry == null)
        {
            throw new RuntimeException("关联询盘不存在: " + quote.getInquiryId());
        }
        if (TradeInquiryService.INQUIRY_STATUS_DEAL.equals(inquiry.getStatus())
                || TradeInquiryService.INQUIRY_STATUS_CLOSED.equals(inquiry.getStatus()))
        {
            throw new RuntimeException("询盘已达成或已关闭，不可报价: " + inquiry.getStatus());
        }
        // P1-08：报价入口过期校验——询盘 expire_at 已过则不可再报价（Quartz 任务关闭前的兼容校验）
        if (inquiry.getExpireAt() != null && inquiry.getExpireAt().before(new Date()))
        {
            throw new RuntimeException("询盘已过期，不可报价");
        }

        quote.setQuoterRole(forcedRole);
        quote.setSellerId(inquiry.getSellerId());
        if (quote.getQuoterId() == null)
        {
            quote.setQuoterId(QUOTER_ROLE_BUYER.equals(forcedRole)
                    ? inquiry.getBuyerId() : inquiry.getSellerId());
        }
        if (quote.getLicenseType() == null || quote.getLicenseType().isEmpty())
        {
            quote.setLicenseType(inquiry.getLicenseType());
        }
        if (quote.getValidDays() == null || quote.getValidDays() <= 0)
        {
            quote.setValidDays(DEFAULT_QUOTE_VALID_DAYS);
        }
        quote.setQuoteNo("QT" + Seq.getId());
        quote.setStatus(QUOTE_PENDING);
        quote.setExpireAt(daysFromNow(quote.getValidDays()));
        quote.setCreateBy(SecurityUtils.getUsername());
        quote.setCreateTime(new Date());
        quoteMapper.insertQuote(quote);

        // 提交报价后询盘进入 quoted（PRD APP-CREATOR-03 待回复→已报价）
        if (TradeInquiryService.INQUIRY_STATUS_PENDING.equals(inquiry.getStatus()))
        {
            SysInquiry update = new SysInquiry();
            update.setInquiryId(inquiry.getInquiryId());
            update.setStatus(TradeInquiryService.INQUIRY_STATUS_QUOTED);
            update.setUpdateBy(SecurityUtils.getUsername());
            update.setUpdateTime(new Date());
            inquiryMapper.updateInquiry(update);
        }
        return quote;
    }

    /**
     * 修改报价（分工条目 18）：仅 pending 可改（accepted/rejected/expired 不可），改动经 @Log 留痕。
     */
    @Transactional
    public int modifyQuote(Long quoteId, SysQuote patch)
    {
        SysQuote quote = quoteMapper.selectQuoteById(quoteId);
        if (quote == null)
        {
            throw new RuntimeException("报价不存在: " + quoteId);
        }
        if (!QUOTE_PENDING.equals(quote.getStatus()))
        {
            throw new RuntimeException("仅待确认报价可修改，当前状态: " + quote.getStatus());
        }
        SysQuote update = new SysQuote();
        update.setQuoteId(quoteId);
        update.setPrice(patch.getPrice());
        update.setLicenseType(patch.getLicenseType());
        update.setDescription(patch.getDescription());
        Integer validDays = patch.getValidDays();
        update.setValidDays(validDays);
        if (validDays != null && validDays > 0)
        {
            update.setExpireAt(daysFromNow(validDays));
        }
        update.setUpdateBy(SecurityUtils.getUsername());
        update.setUpdateTime(new Date());
        return quoteMapper.updateQuote(update);
    }

    /** 当前时间 + days 天 */
    private Date daysFromNow(int days)
    {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    /**
     * 接受报价：状态机校验（仅 pending）+ 过期校验 → 生成订单（幂等）→ 报价置 accepted、询盘置 deal。
     */
    @Transactional
    public SysOrder acceptQuote(Long quoteId)
    {
        SysQuote quote = quoteMapper.selectQuoteById(quoteId);
        if (quote == null)
        {
            throw new RuntimeException("报价不存在: " + quoteId);
        }
        if (!QUOTE_PENDING.equals(quote.getStatus()))
        {
            throw new RuntimeException("当前报价状态不可接受: " + quote.getStatus());
        }
        if (quote.getExpireAt() != null && quote.getExpireAt().before(new Date()))
        {
            // 过期报价不可接受，顺带纠正状态
            updateQuoteStatus(quoteId, QUOTE_EXPIRED);
            throw new RuntimeException("报价已过期，不可接受");
        }

        SysInquiry inquiry = inquiryMapper.selectInquiryById(quote.getInquiryId());
        if (inquiry == null)
        {
            throw new RuntimeException("报价关联的询盘不存在: " + quote.getInquiryId());
        }

        SysOrder order = orderService.generateOrderFromInquiry(
                inquiry, quoteId, quote.getPrice(), "quote", "Quote accepted, order generated");

        updateQuoteStatus(quoteId, QUOTE_ACCEPTED);

        // 询盘置 deal（幂等：convertToOrder 内已判重，此处直接更新）
        if (!TradeInquiryService.INQUIRY_STATUS_DEAL.equals(inquiry.getStatus()))
        {
            SysInquiry update = new SysInquiry();
            update.setInquiryId(inquiry.getInquiryId());
            update.setStatus(TradeInquiryService.INQUIRY_STATUS_DEAL);
            update.setUpdateBy(SecurityUtils.getUsername());
            update.setUpdateTime(new Date());
            inquiryMapper.updateInquiry(update);
        }

        return order;
    }

    /**
     * 拒绝报价：状态机校验（仅 pending）→ 置 rejected。
     */
    @Transactional
    public int rejectQuote(Long quoteId)
    {
        SysQuote quote = quoteMapper.selectQuoteById(quoteId);
        if (quote == null)
        {
            throw new RuntimeException("报价不存在: " + quoteId);
        }
        if (!QUOTE_PENDING.equals(quote.getStatus()))
        {
            throw new RuntimeException("当前报价状态不可拒绝: " + quote.getStatus());
        }
        return updateQuoteStatus(quoteId, QUOTE_REJECTED);
    }

    public int updateQuoteStatus(Long quoteId, String status)
    {
        SysQuote quote = new SysQuote();
        quote.setQuoteId(quoteId);
        quote.setStatus(status);
        quote.setUpdateBy(SecurityUtils.getUsername());
        quote.setUpdateTime(new Date());
        return quoteMapper.updateQuote(quote);
    }
}
