package com.smartscript.platform.trade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.domain.SysBillRecord;
import com.smartscript.platform.trade.domain.SysEscrow;
import com.smartscript.platform.trade.domain.SysInvoice;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.mapper.SysBillRecordMapper;
import com.smartscript.platform.trade.mapper.SysEscrowMapper;
import com.smartscript.platform.trade.mapper.SysFinanceStatsMapper;
import com.smartscript.platform.trade.mapper.SysInvoiceMapper;
import com.smartscript.platform.trade.mapper.SysOrderMapper;

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
    private final SysOrderMapper orderMapper;
    private final JdbcTemplate jdbc;

    public TradeFinanceService(SysEscrowMapper escrowMapper,
                               SysInvoiceMapper invoiceMapper,
                               SysBillRecordMapper billRecordMapper,
                               SysFinanceStatsMapper financeStatsMapper,
                               SysOrderMapper orderMapper,
                               JdbcTemplate jdbc)
    {
        this.escrowMapper = escrowMapper;
        this.invoiceMapper = invoiceMapper;
        this.billRecordMapper = billRecordMapper;
        this.financeStatsMapper = financeStatsMapper;
        this.orderMapper = orderMapper;
        this.jdbc = jdbc;
    }

    /* ========== 收益总览 ========== */

    /** 收益结算平台抽成：仅作用于待结算/入账，不改订单本身金额。 */
    public static final BigDecimal PLATFORM_SPLIT_RATE = new BigDecimal("0.05");

    /**
     * 查询作者收益总览（从 sys_income_account + 账单累计 + 待结算）。
     * <p>
     * 字段口径：
     * totalIncome = 累计已入账作者实得（确认结算后计入，提现不扣减）；
     * pendingSettleAmount = 待作者确认、尚未入账（已扣 5% 平台抽成后的作者实得）；
     * availableAmount = 可提现；
     * withdrawnAmount = 已提现（含审核中/已通过/已打款，不含已驳回）。
     * 待结算未入账，因此总收益 = 可提现口径的累计入账，不含待结算。
     * 不使用 sys_creator_stats.total_income：该表按日插入且无累计唯一键，最新一行只是当日增量。
     */
    public Map<String, Object> getIncomeOverview(Long userId)
    {
        Map<String, Object> overview = new HashMap<>();

        BigDecimal available = BigDecimal.ZERO;
        BigDecimal frozen = BigDecimal.ZERO;
        BigDecimal settled = BigDecimal.ZERO;
        Map<String, Object> account = financeStatsMapper.selectIncomeAccount(userId);
        if (account != null)
        {
            available = toDecimal(account.get("availableAmount"));
            frozen = toDecimal(account.get("frozenAmount"));
            settled = toDecimal(account.get("settledAmount"));
        }
        overview.put("availableAmount", available);
        overview.put("frozenAmount", frozen);
        overview.put("settledAmount", settled);

        Map<String, Object> stats = financeStatsMapper.selectLatestCreatorStats(userId);
        if (stats != null)
        {
            overview.put("monthIncome", stats.get("monthIncome"));
            overview.put("totalOrders", stats.get("totalOrders"));
            overview.put("soldCount", stats.get("soldCount"));
        }
        else
        {
            overview.put("monthIncome", BigDecimal.ZERO);
            overview.put("totalOrders", 0);
            overview.put("soldCount", 0);
        }

        BigDecimal credited = queryCreditedIncome(userId);
        if (settled.compareTo(credited) > 0)
        {
            credited = settled;
        }
        overview.put("totalIncome", credited);

        overview.put("pendingSettleAmount", queryPendingSettleAmount(userId));
        overview.put("pendingSettleCount", countPendingSettle(userId));
        overview.put("withdrawnAmount", queryWithdrawnAmount(userId));

        return overview;
    }

    /** 已提现合计：提现申请中未驳回的金额。 */
    private BigDecimal queryWithdrawnAmount(Long userId)
    {
        try
        {
            BigDecimal amount = jdbc.queryForObject(
                    "SELECT IFNULL(SUM(amount), 0) FROM sys_withdraw_apply "
                            + "WHERE user_id = ? AND status IN ('pending','approved','frozen','paid')",
                    BigDecimal.class, userId);
            return amount != null ? amount : BigDecimal.ZERO;
        }
        catch (Exception ex)
        {
            return BigDecimal.ZERO;
        }
    }

    /** 累计已入账收益：收入类账单合计（提现不扣减）。 */
    private BigDecimal queryCreditedIncome(Long userId)
    {
        try
        {
            BigDecimal amount = jdbc.queryForObject(
                    "SELECT IFNULL(SUM(amount), 0) FROM sys_bill_record "
                            + "WHERE user_id = ? AND bill_type = 'income'",
                    BigDecimal.class, userId);
            return amount != null ? amount : BigDecimal.ZERO;
        }
        catch (Exception ex)
        {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal queryPendingSettleAmount(Long userId)
    {
        BigDecimal amount = jdbc.queryForObject(
                "SELECT IFNULL(SUM(seller_amount), 0) FROM sys_income_settle "
                        + "WHERE user_id = ? AND status = 'pending'",
                BigDecimal.class, userId);
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private long countPendingSettle(Long userId)
    {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_income_settle WHERE user_id = ? AND status = 'pending'",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    /* ========== 待结算（到账前作者确认/异议） ========== */

    private static final String SETTLE_SELECT =
            "SELECT settle_id AS settleId, settle_no AS settleNo, user_id AS userId, order_id AS orderId, "
                    + "order_no AS orderNo, work_title AS workTitle, total_amount AS totalAmount, "
                    + "platform_fee AS platformFee, split_rate AS splitRate, seller_amount AS sellerAmount, "
                    + "status AS status, dispute_reason AS disputeReason, bill_id AS billId, "
                    + "DATE_FORMAT(confirm_time, '%Y-%m-%d %H:%i:%s') AS confirmTime, "
                    + "DATE_FORMAT(dispute_time, '%Y-%m-%d %H:%i:%s') AS disputeTime, "
                    + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
                    + "FROM sys_income_settle ";

    /**
     * 订单完成后创建待结算单（不入账）。同一 order_id 幂等：已存在则直接返回。
     */
    @Transactional
    public Map<String, Object> createPendingSettle(SysOrder order)
    {
        if (order == null || order.getOrderId() == null || order.getSellerId() == null)
        {
            throw new IllegalArgumentException("订单信息不完整，无法创建待结算");
        }
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) <= 0 && order.getSellerAmount() != null)
        {
            total = order.getSellerAmount();
        }
        BigDecimal[] split = splitSettleAmounts(total);
        BigDecimal fee = split[0];
        BigDecimal rate = split[1];
        BigDecimal sellerAmount = split[2];
        if (sellerAmount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new IllegalArgumentException("作者应得金额无效，无法创建待结算");
        }

        List<Map<String, Object>> existed = jdbc.queryForList(
                SETTLE_SELECT + " WHERE order_id = ? LIMIT 1", order.getOrderId());
        if (!existed.isEmpty())
        {
            return applyPendingPlatformSplit(existed.get(0));
        }

        String settleNo = "ST" + Seq.getId();

        jdbc.update(
                "INSERT INTO sys_income_settle (settle_no, user_id, order_id, order_no, work_title, "
                        + "total_amount, platform_fee, split_rate, seller_amount, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', NOW(), NOW())",
                settleNo, order.getSellerId(), order.getOrderId(), order.getOrderNo(),
                order.getWorkTitle(), total, fee, rate, sellerAmount);

        List<Map<String, Object>> created = jdbc.queryForList(
                SETTLE_SELECT + " WHERE settle_no = ? LIMIT 1", settleNo);
        return created.isEmpty() ? Map.of("settleNo", settleNo, "status", "pending") : created.get(0);
    }

    public long countSettleList(Long userId, String status)
    {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sys_income_settle WHERE user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (status != null && !status.isBlank())
        {
            sql.append(" AND status = ?");
            args.add(status.trim());
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> getSettleList(Long userId, String status, int pageNum, int pageSize)
    {
        StringBuilder sql = new StringBuilder(SETTLE_SELECT + " WHERE user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (status != null && !status.isBlank())
        {
            sql.append(" AND status = ?");
            args.add(status.trim());
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(pageSize);
        args.add((long) (pageNum - 1) * pageSize);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (int i = 0; i < rows.size(); i++)
        {
            rows.set(i, applyPendingPlatformSplit(rows.get(i)));
        }
        return rows;
    }

    public Map<String, Object> getSettleDetail(Long userId, Long settleId)
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                SETTLE_SELECT + " WHERE settle_id = ? AND user_id = ? LIMIT 1", settleId, userId);
        if (rows.isEmpty())
        {
            throw new IllegalArgumentException("待结算单不存在");
        }
        return applyPendingPlatformSplit(rows.get(0));
    }

    /**
     * 作者确认结算 → 入账到可提现，并写收益账单。
     */
    @Transactional
    public Map<String, Object> confirmSettle(Long userId, Long settleId)
    {
        Map<String, Object> settle = getSettleDetail(userId, settleId);
        String status = String.valueOf(settle.get("status"));
        if (!"pending".equals(status) && !"disputed".equals(status))
        {
            throw new IllegalStateException("仅待确认或有异议的结算单可以确认入账");
        }

        int changed = jdbc.update(
                "UPDATE sys_income_settle SET status = 'confirmed', confirm_time = NOW(), "
                        + "dispute_reason = NULL, updated_at = NOW() "
                        + "WHERE settle_id = ? AND user_id = ? AND status IN ('pending', 'disputed')",
                settleId, userId);
        if (changed != 1)
        {
            throw new IllegalStateException("结算单状态已变化，请刷新后重试");
        }

        Long orderId = ((Number) settle.get("orderId")).longValue();
        BigDecimal amount = toDecimal(settle.get("sellerAmount"));
        Long billId = creditSellerIncome(userId, amount, orderId);

        jdbc.update("UPDATE sys_income_settle SET bill_id = ?, updated_at = NOW() WHERE settle_id = ?",
                billId, settleId);

        Map<String, Object> result = new HashMap<>(settle);
        result.put("status", "confirmed");
        result.put("billId", billId);
        return result;
    }

    /**
     * 作者提出异议（不入账），须填写原因。
     */
    @Transactional
    public Map<String, Object> disputeSettle(Long userId, Long settleId, String reason)
    {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty() || text.length() > 500)
        {
            throw new IllegalArgumentException("异议原因须为 1 至 500 个字符");
        }

        int changed = jdbc.update(
                "UPDATE sys_income_settle SET status = 'disputed', dispute_reason = ?, dispute_time = NOW(), "
                        + "updated_at = NOW() WHERE settle_id = ? AND user_id = ? AND status = 'pending'",
                text, settleId, userId);
        if (changed != 1)
        {
            throw new IllegalStateException("仅待确认的结算单可以提出异议");
        }
        return getSettleDetail(userId, settleId);
    }

    /**
     * 账单详情（含订单分成字段，供收益明细详情页）。
     */
    public Map<String, Object> getBillDetail(Long userId, Long billId)
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT b.bill_id AS billId, b.bill_no AS billNo, b.bill_type AS billType, b.amount AS amount, "
                        + "b.balance_after AS balanceAfter, b.related_type AS relatedType, b.related_id AS relatedId, "
                        + "b.remark AS remark, DATE_FORMAT(b.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt, "
                        + "o.order_no AS orderNo, o.status AS orderStatus, "
                        + "COALESCE(s.total_amount, o.total_amount) AS totalAmount, "
                        + "COALESCE(s.platform_fee, o.platform_fee) AS platformFee, "
                        + "COALESCE(s.split_rate, o.split_rate) AS splitRate, "
                        + "COALESCE(s.seller_amount, o.seller_amount) AS sellerAmount, "
                        + "DATE_FORMAT(o.pay_time, '%Y-%m-%d %H:%i:%s') AS payTime, "
                        + "DATE_FORMAT(o.complete_time, '%Y-%m-%d %H:%i:%s') AS completeTime, "
                        + "w.title AS workTitle "
                        + "FROM sys_bill_record b "
                        + "LEFT JOIN sys_order o ON b.related_type = 'order' AND b.related_id = o.order_id "
                        + "LEFT JOIN sys_income_settle s ON s.order_id = o.order_id "
                        + "LEFT JOIN sys_work w ON o.work_id = w.work_id "
                        + "WHERE b.bill_id = ? AND b.user_id = ? LIMIT 1",
                billId, userId);
        if (rows.isEmpty())
        {
            throw new IllegalArgumentException("账单不存在");
        }
        return rows.get(0);
    }

    /* ========== 提现（App） ========== */

    private static final List<String> WITHDRAW_TYPES = List.of("bank", "alipay", "wechat");
    /** 超过该金额（元）需管理员人工审核；小于等于该金额申请后自动通过。 */
    public static final BigDecimal WITHDRAW_AUTO_APPROVE_LIMIT = new BigDecimal("10000");

    /**
     * App / 创作者端发起提现：校验余额 → 扣减可用金额 → 写入申请单 → 记账。
     * <p>
     * 金额 ≤ 10000 元自动通过（approved）；超过 10000 元进入 pending，由管理员审核。
     * 申请时即扣减可用余额，避免重复提现；驳回时退回。
     * 幂等：同一 userId + clientToken 重复提交返回已有申请；同一用户同时只允许一笔 pending。
     */
    @Transactional
    public Map<String, Object> applyWithdraw(Long userId, BigDecimal amount, String withdrawType,
                                             String accountName, String accountNo, String bankName,
                                             String clientToken)
    {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new IllegalArgumentException("提现金额必须大于 0");
        }
        if (amount.scale() > 2)
        {
            throw new IllegalArgumentException("提现金额最多两位小数");
        }
        String type = withdrawType == null ? "" : withdrawType.trim();
        if (!WITHDRAW_TYPES.contains(type))
        {
            throw new IllegalArgumentException("提现方式无效，仅支持 bank/alipay/wechat");
        }
        String name = requireText(accountName, "账户名", 100);
        String no = requireText(accountNo, "账号", 100);
        String bank = bankName == null ? null : bankName.trim();
        if ("bank".equals(type) && (bank == null || bank.isEmpty()))
        {
            throw new IllegalArgumentException("银行卡提现须填写开户行");
        }
        if (bank != null && bank.length() > 100)
        {
            bank = bank.substring(0, 100);
        }

        String token = clientToken == null ? null : clientToken.trim();
        if (token != null && !token.isEmpty())
        {
            if (token.length() > 64)
            {
                throw new IllegalArgumentException("clientToken 长度不能超过 64");
            }
            List<Map<String, Object>> existed = jdbc.queryForList(
                    "SELECT withdraw_id AS withdrawId, withdraw_no AS withdrawNo, status AS status "
                            + "FROM sys_withdraw_apply WHERE user_id = ? AND client_token = ? LIMIT 1",
                    userId, token);
            if (!existed.isEmpty())
            {
                return existed.get(0);
            }
        }
        else
        {
            token = null;
        }

        Integer pendingCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_withdraw_apply WHERE user_id = ? AND status = 'pending'",
                Integer.class, userId);
        if (pendingCount != null && pendingCount > 0)
        {
            throw new IllegalStateException("已有待审核提现申请，请勿重复提交");
        }

        Map<String, Object> account = financeStatsMapper.selectIncomeAccount(userId);
        if (account == null)
        {
            throw new IllegalStateException("收益账户不存在");
        }
        BigDecimal available = toDecimal(account.get("availableAmount"));
        if (available.compareTo(amount) < 0)
        {
            throw new IllegalStateException("可提现余额不足");
        }

        int deducted = jdbc.update(
                "UPDATE sys_income_account SET available_amount = available_amount - ?, "
                        + "version = version + 1, updated_at = NOW() "
                        + "WHERE user_id = ? AND available_amount >= ?",
                amount, userId, amount);
        if (deducted != 1)
        {
            throw new IllegalStateException("余额变更失败，请重试");
        }

        Map<String, Object> after = financeStatsMapper.selectIncomeAccount(userId);
        BigDecimal balanceAfter = after != null ? toDecimal(after.get("availableAmount")) : available.subtract(amount);

        boolean autoApprove = amount.compareTo(WITHDRAW_AUTO_APPROVE_LIMIT) <= 0;
        String status = autoApprove ? "approved" : "pending";
        String withdrawNo = "WD" + Seq.getId();
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal actual = amount.subtract(fee);
        if (autoApprove)
        {
            jdbc.update(
                    "INSERT INTO sys_withdraw_apply (withdraw_no, user_id, amount, fee, actual_amount, "
                            + "withdraw_type, account_name, account_no, bank_name, status, client_token, "
                            + "review_time, review_opinion, update_by, update_time, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, NOW(), NOW())",
                    withdrawNo, userId, amount, fee, actual, type, name, no, bank, status, token,
                    "系统自动通过（金额不超过10000元）", "system");
        }
        else
        {
            jdbc.update(
                    "INSERT INTO sys_withdraw_apply (withdraw_no, user_id, amount, fee, actual_amount, "
                            + "withdraw_type, account_name, account_no, bank_name, status, client_token, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                    withdrawNo, userId, amount, fee, actual, type, name, no, bank, status, token);
        }

        Long withdrawId = jdbc.queryForObject(
                "SELECT withdraw_id FROM sys_withdraw_apply WHERE withdraw_no = ?", Long.class, withdrawNo);

        String billNo = "BILL" + Seq.getId();
        Map<String, Object> billParams = new HashMap<>();
        billParams.put("billNo", billNo);
        billParams.put("userId", userId);
        billParams.put("billType", "withdraw");
        billParams.put("amount", amount.negate());
        billParams.put("balanceAfter", balanceAfter);
        billParams.put("relatedType", "withdraw");
        billParams.put("relatedId", withdrawId);
        billParams.put("remark", autoApprove ? "提现自动通过" : "提现申请待审核");
        financeStatsMapper.insertBillRecord(billParams);

        Map<String, Object> result = new HashMap<>();
        result.put("withdrawId", withdrawId);
        result.put("withdrawNo", withdrawNo);
        result.put("status", status);
        return result;
    }

    /**
     * App 提现记录总数。
     */
    public long countWithdrawList(Long userId, String status)
    {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sys_withdraw_apply WHERE user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (status != null && !status.isBlank())
        {
            sql.append(" AND status = ?");
            args.add(status.trim());
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    /**
     * App 提现记录列表（含驳回原因 reviewOpinion；JdbcTemplate 手动分页）。
     */
    public List<Map<String, Object>> getWithdrawList(Long userId, String status, int pageNum, int pageSize)
    {
        StringBuilder sql = new StringBuilder(
                "SELECT withdraw_id AS withdrawId, withdraw_no AS withdrawNo, amount AS amount, "
                        + "fee AS fee, actual_amount AS actualAmount, withdraw_type AS withdrawType, "
                        + "account_name AS accountName, account_no AS accountNo, bank_name AS bankName, "
                        + "status AS status, review_opinion AS reviewOpinion, "
                        + "DATE_FORMAT(review_time, '%Y-%m-%d %H:%i:%s') AS reviewTime, "
                        + "DATE_FORMAT(pay_time, '%Y-%m-%d %H:%i:%s') AS payTime, "
                        + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
                        + "FROM sys_withdraw_apply WHERE user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (status != null && !status.isBlank())
        {
            sql.append(" AND status = ?");
            args.add(status.trim());
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(pageSize);
        args.add((long) (pageNum - 1) * pageSize);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private static String requireText(String value, String label, int maxLen)
    {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > maxLen)
        {
            throw new IllegalArgumentException(label + "须为 1 至 " + maxLen + " 个字符");
        }
        return text;
    }

    private static BigDecimal toDecimal(Object value)
    {
        if (value instanceof BigDecimal bd)
        {
            return bd;
        }
        if (value instanceof Number n)
        {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    /**
     * 按订单总额计算结算抽成。返回 [platformFee, splitRate, sellerAmount]。
     * 不改写 sys_order，避免影响合同/托管/发票等其它功能。
     */
    static BigDecimal[] splitSettleAmounts(BigDecimal totalAmount)
    {
        BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) < 0)
        {
            total = BigDecimal.ZERO;
        }
        BigDecimal fee = total.multiply(PLATFORM_SPLIT_RATE).setScale(2, RoundingMode.HALF_UP);
        if (fee.compareTo(total) > 0)
        {
            fee = total;
        }
        BigDecimal seller = total.subtract(fee);
        return new BigDecimal[] { fee, PLATFORM_SPLIT_RATE, seller };
    }

    /**
     * 未入账结算单若仍是旧的 0 抽成快照，补算 5% 并写回，保证详情/确认金额一致。
     * 已确认入账的历史单不改。
     */
    private Map<String, Object> applyPendingPlatformSplit(Map<String, Object> settle)
    {
        if (settle == null || settle.isEmpty())
        {
            return settle;
        }
        String status = String.valueOf(settle.get("status"));
        if (!"pending".equals(status) && !"disputed".equals(status))
        {
            return settle;
        }
        BigDecimal total = toDecimal(settle.get("totalAmount"));
        BigDecimal[] split = splitSettleAmounts(total);
        BigDecimal fee = split[0];
        BigDecimal rate = split[1];
        BigDecimal seller = split[2];
        if (toDecimal(settle.get("platformFee")).compareTo(fee) == 0
                && toDecimal(settle.get("splitRate")).compareTo(rate) == 0
                && toDecimal(settle.get("sellerAmount")).compareTo(seller) == 0)
        {
            return settle;
        }
        Object settleId = settle.get("settleId");
        if (settleId != null)
        {
            jdbc.update(
                    "UPDATE sys_income_settle SET platform_fee = ?, split_rate = ?, seller_amount = ?, "
                            + "updated_at = NOW() WHERE settle_id = ? AND status IN ('pending', 'disputed')",
                    fee, rate, seller, settleId);
        }
        settle.put("platformFee", fee);
        settle.put("splitRate", rate);
        settle.put("sellerAmount", seller);
        return settle;
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

    /**
     * 收入/提现统计（按周、月、年）。
     * <p>
     * period: week / month / year；金额单位元。
     */
    public Map<String, Object> getIncomeStats(Long userId, String period)
    {
        String p = period == null ? "month" : period.trim().toLowerCase();
        if (!List.of("week", "month", "year").contains(p))
        {
            throw new IllegalArgumentException("统计周期仅支持 week/month/year");
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate startDate;
        switch (p)
        {
            case "week" -> startDate = today.with(java.time.DayOfWeek.MONDAY);
            case "year" -> startDate = today.withDayOfYear(1);
            default -> startDate = today.withDayOfMonth(1);
        }
        java.time.LocalDateTime start = startDate.atStartOfDay();
        java.time.LocalDateTime end = today.plusDays(1).atStartOfDay();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT IFNULL(SUM(CASE WHEN bill_type = 'income' THEN amount ELSE 0 END), 0) AS incomeAmount, "
                        + "IFNULL(SUM(CASE WHEN bill_type = 'withdraw' THEN ABS(amount) ELSE 0 END), 0) AS withdrawAmount "
                        + "FROM sys_bill_record WHERE user_id = ? AND created_at >= ? AND created_at < ?",
                userId, java.sql.Timestamp.valueOf(start), java.sql.Timestamp.valueOf(end));

        Map<String, Object> result = new HashMap<>();
        result.put("period", p);
        result.put("periodStart", startDate.toString());
        result.put("periodEnd", today.toString());
        result.put("incomeAmount", toDecimal(row.get("incomeAmount")));
        result.put("withdrawAmount", toDecimal(row.get("withdrawAmount")));
        return result;
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
     * <p>
     * 校验链（修复前金额/归属/状态/重复四项均不设防）：
     * 订单存在 → 申请人须为订单卖方 → 订单已完成 → 同订单不存在非 rejected 发票；
     * 发票金额一律以订单 total_amount 为准，不信任客户端传入值。
     */
    @Transactional
    public int applyInvoice(SysInvoice invoice)
    {
        SysOrder order = orderMapper.selectOrderById(invoice.getOrderId());
        if (order == null)
        {
            throw new RuntimeException("订单不存在，无法申请发票");
        }
        if (!invoice.getApplicantId().equals(order.getSellerId()))
        {
            throw new RuntimeException("只有卖方可以申请该订单的发票");
        }
        if (!"completed".equals(order.getStatus()))
        {
            throw new RuntimeException("仅已完成订单可以申请发票");
        }
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_invoice WHERE order_id = ? AND status <> 'rejected'",
                Integer.class, invoice.getOrderId());
        if (existing != null && existing > 0)
        {
            throw new RuntimeException("该订单已申请发票，请勿重复申请");
        }
        invoice.setAmount(order.getTotalAmount());
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
     * 将卖家收益写入账户 + 账单流水 + 统计（作者确认结算后调用）。
     *
     * @return 新建账单 ID
     */
    @Transactional
    public Long creditSellerIncome(Long sellerId, BigDecimal amount, Long orderId)
    {
        // 1. UPSERT 收益账户
        Map<String, Object> accountParams = new HashMap<>();
        accountParams.put("userId", sellerId);
        accountParams.put("amount", amount);
        financeStatsMapper.upsertIncomeAccount(accountParams);

        // 2. 查当前余额（用于记录 balance_after）
        Map<String, Object> account = financeStatsMapper.selectIncomeAccount(sellerId);
        BigDecimal balanceAfter = account != null
                ? toDecimal(account.get("availableAmount"))
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
        billParams.put("remark", "作者确认结算后入账（已扣除5%平台抽成）");
        financeStatsMapper.insertBillRecord(billParams);

        // 4. UPSERT 作者统计
        Map<String, Object> statsParams = new HashMap<>();
        statsParams.put("userId", sellerId);
        statsParams.put("amount", amount);
        financeStatsMapper.upsertCreatorStats(statsParams);

        Long billId = jdbc.queryForObject(
                "SELECT bill_id FROM sys_bill_record WHERE bill_no = ? LIMIT 1", Long.class, billNo);
        return billId;
    }
}
