package com.smartscript.platform.trade.service;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.domain.SysBillRecord;
import com.smartscript.platform.trade.domain.SysEscrow;
import com.smartscript.platform.trade.domain.SysInvoice;
import com.smartscript.platform.trade.mapper.SysBillRecordMapper;
import com.smartscript.platform.trade.mapper.SysEscrowMapper;
import com.smartscript.platform.trade.mapper.SysFinanceStatsMapper;
import com.smartscript.platform.trade.mapper.SysInvoiceMapper;

/**
 * 财务域服务（资金托管 / 发票 / 收益）。
 */
@Service
public class TradeFinanceService
{
    private final SysEscrowMapper escrowMapper;
    private final SysInvoiceMapper invoiceMapper;
    private final SysBillRecordMapper billRecordMapper;
    private final SysFinanceStatsMapper financeStatsMapper;

    public TradeFinanceService(SysEscrowMapper escrowMapper,
                               SysInvoiceMapper invoiceMapper,
                               SysBillRecordMapper billRecordMapper,
                               SysFinanceStatsMapper financeStatsMapper)
    {
        this.escrowMapper = escrowMapper;
        this.invoiceMapper = invoiceMapper;
        this.billRecordMapper = billRecordMapper;
        this.financeStatsMapper = financeStatsMapper;
    }

    /* ========== 收益总览 ========== */

    /**
     * 查询作者收益总览（从 sys_income_account + sys_creator_stats）。
     */
    public Map<String, Object> getIncomeOverview(Long userId)
    {
        Map<String, Object> overview = new HashMap<>();

        // 查收益账户
        Map<String, Object> account = financeStatsMapper.selectIncomeAccount(userId);
        if (account != null)
        {
            overview.put("availableAmount", account.get("availableAmount"));
            overview.put("frozenAmount", account.get("frozenAmount"));
            overview.put("settledAmount", account.get("settledAmount"));
        }
        else
        {
            overview.put("availableAmount", BigDecimal.ZERO);
            overview.put("frozenAmount", BigDecimal.ZERO);
            overview.put("settledAmount", BigDecimal.ZERO);
        }

        // 查最新统计
        Map<String, Object> stats = financeStatsMapper.selectLatestCreatorStats(userId);
        if (stats != null)
        {
            overview.put("totalIncome", stats.get("totalIncome"));
            overview.put("monthIncome", stats.get("monthIncome"));
            overview.put("totalOrders", stats.get("totalOrders"));
            overview.put("soldCount", stats.get("soldCount"));
        }
        else
        {
            overview.put("totalIncome", BigDecimal.ZERO);
            overview.put("monthIncome", BigDecimal.ZERO);
            overview.put("totalOrders", 0);
            overview.put("soldCount", 0);
        }

        return overview;
    }

    /**
     * 收益明细列表（账单流水）。
     */
    public List<SysBillRecord> getBillRecordList(Long userId, String billType)
    {
        SysBillRecord query = new SysBillRecord();
        query.setUserId(userId);
        if (billType != null && !billType.isEmpty())
        {
            query.setBillType(billType);
        }
        return billRecordMapper.selectBillRecordList(query);
    }

    /* ========== 资金托管 ========== */

    public List<SysEscrow> getEscrowList(Long userId, String role, String status)
    {
        SysEscrow query = new SysEscrow();
        if ("buyer".equals(role))
        {
            query.setBuyerId(userId);
        }
        else if ("seller".equals(role))
        {
            query.setSellerId(userId);
        }
        if (status != null && !status.isEmpty())
        {
            query.setStatus(status);
        }
        return escrowMapper.selectEscrowList(query);
    }

    public SysEscrow getEscrowDetail(Long escrowId)
    {
        return escrowMapper.selectEscrowById(escrowId);
    }

    /* ========== 发票 ========== */

    public List<SysInvoice> getInvoiceList(Long userId, String status)
    {
        SysInvoice query = new SysInvoice();
        query.setApplicantId(userId);
        if (status != null && !status.isEmpty())
        {
            query.setStatus(status);
        }
        return invoiceMapper.selectInvoiceList(query);
    }

    public SysInvoice getInvoiceDetail(Long invoiceId)
    {
        return invoiceMapper.selectInvoiceById(invoiceId);
    }

    /**
     * 可开票订单列表（已完成且未开过发票）。
     */
    public List<Map<String, Object>> getInvoiceableOrders(Long userId)
    {
        return financeStatsMapper.selectInvoiceableOrders(userId);
    }

    /**
     * 申请发票。
     */
    public int applyInvoice(SysInvoice invoice)
    {
        invoice.setInvoiceNo(generateInvoiceNo());
        return invoiceMapper.insertInvoice(invoice);
    }

    /**
     * 生成发票编号（简化版：INV + 时间戳 + 随机数）。
     */
    private String generateInvoiceNo()
    {
        return "INV" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }

    /* ========== 收益入账（订单完成时调用） ========== */

    /**
     * 订单完成后，将卖家收益写入账户 + 账单流水 + 统计。
     *
     * @param sellerId 卖家用户 ID
     * @param amount   卖家应得金额（seller_amount）
     * @param orderId  关联订单 ID
     */
    @Transactional
    public void creditSellerIncome(Long sellerId, BigDecimal amount, Long orderId)
    {
        // 1. UPSERT 收益账户
        Map<String, Object> accountParams = new HashMap<>();
        accountParams.put("userId", sellerId);
        accountParams.put("amount", amount);
        financeStatsMapper.upsertIncomeAccount(accountParams);

        // 2. 查当前余额（用于记录 balance_after）
        Map<String, Object> account = financeStatsMapper.selectIncomeAccount(sellerId);
        BigDecimal balanceAfter = account != null
                ? (BigDecimal) account.getOrDefault("availableAmount", BigDecimal.ZERO)
                : amount;

        // 3. INSERT 账单流水
        String billNo = "BILL" + Seq.getId();
        Map<String, Object> billParams = new HashMap<>();
        billParams.put("billNo", billNo);
        billParams.put("userId", sellerId);
        billParams.put("billType", "income");
        billParams.put("amount", amount);
        billParams.put("balanceAfter", balanceAfter);
        billParams.put("relatedType", "order");
        billParams.put("relatedId", orderId);
        billParams.put("remark", "订单完成收益入账");
        financeStatsMapper.insertBillRecord(billParams);

        // 4. UPSERT 作者统计
        Map<String, Object> statsParams = new HashMap<>();
        statsParams.put("userId", sellerId);
        statsParams.put("amount", amount);
        financeStatsMapper.upsertCreatorStats(statsParams);
    }
}
