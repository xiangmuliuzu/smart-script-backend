package com.ruoyi.web.service.pc;

import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WithdrawReviewService {
    private static final String SELECT = "SELECT w.withdraw_id AS withdrawId, w.withdraw_no AS withdrawNo, "
            + "w.user_id AS userId, COALESCE(NULLIF(u.nick_name, ''), u.user_name) AS userName, "
            + "u.user_name AS userAccount, w.amount AS amount, w.fee AS fee, w.actual_amount AS actualAmount, "
            + "w.withdraw_type AS withdrawType, w.account_name AS accountName, w.account_no AS accountNo, "
            + "w.bank_name AS bankName, w.reviewer_id AS reviewerId, w.review_time AS reviewTime, "
            + "w.review_opinion AS reviewOpinion, w.status AS status, w.pay_time AS payTime, "
            + "w.pay_trade_no AS payTradeNo, DATE_FORMAT(w.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
            + "FROM sys_withdraw_apply w LEFT JOIN sys_user u ON u.user_id = w.user_id ";
    private static final List<String> STATUSES = List.of("pending", "approved", "rejected", "frozen", "paid");

    private final JdbcTemplate jdbc;

    public WithdrawReviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count(String status, String keyword) {
        Filter filter = filter(status, keyword);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_withdraw_apply w LEFT JOIN sys_user u "
                + "ON u.user_id = w.user_id " + filter.sql, Long.class, filter.args.toArray());
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> list(String status, String keyword, int pageNum, int pageSize) {
        Filter filter = filter(status, keyword);
        List<Object> args = new ArrayList<>(filter.args);
        args.add(pageSize);
        args.add((long) (pageNum - 1) * pageSize);
        return jdbc.queryForList(SELECT + filter.sql + " ORDER BY w.created_at DESC LIMIT ? OFFSET ?", args.toArray());
    }

    public List<Map<String, Object>> export(String status, String keyword) {
        Filter filter = filter(status, keyword);
        return jdbc.queryForList(SELECT + filter.sql + " ORDER BY w.created_at DESC", filter.args.toArray());
    }

    public Map<String, Object> get(Long withdrawId) {
        try {
            return jdbc.queryForMap(SELECT + " WHERE w.withdraw_id = ?", withdrawId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("提现申请不存在");
        }
    }

    @Transactional
    public void approve(Long withdrawId) {
        Map<String, Object> apply;
        try {
            apply = jdbc.queryForMap(
                    "SELECT user_id AS userId, amount AS amount FROM sys_withdraw_apply "
                            + "WHERE withdraw_id = ? AND status = 'pending'",
                    withdrawId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("仅待审核申请可以通过");
        }
        int changed = jdbc.update("UPDATE sys_withdraw_apply SET status = 'approved', reviewer_id = ?, "
                        + "review_time = NOW(), review_opinion = NULL, update_by = ?, update_time = NOW() "
                        + "WHERE withdraw_id = ? AND status = 'pending'",
                SecurityUtils.getUserId(), SecurityUtils.getUsername(), withdrawId);
        requireChanged(changed, "仅待审核申请可以通过");
        // 旧数据申请时可能写入 frozen_amount；新流程申请时已扣可用余额，此处仅冲减遗留冻结额。
        releaseLegacyFrozen(apply);
    }

    @Transactional
    public void reject(Long withdrawId, String opinion) {
        String reason = opinion == null ? "" : opinion.trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new IllegalArgumentException("驳回原因须为 1 至 500 个字符");
        }
        Map<String, Object> apply;
        try {
            apply = jdbc.queryForMap(
                    "SELECT user_id AS userId, amount AS amount FROM sys_withdraw_apply "
                            + "WHERE withdraw_id = ? AND status = 'pending'",
                    withdrawId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("仅待审核申请可以驳回");
        }
        int changed = jdbc.update("UPDATE sys_withdraw_apply SET status = 'rejected', reviewer_id = ?, "
                        + "review_time = NOW(), review_opinion = ?, update_by = ?, update_time = NOW() "
                        + "WHERE withdraw_id = ? AND status = 'pending'",
                SecurityUtils.getUserId(), reason, SecurityUtils.getUsername(), withdrawId);
        requireChanged(changed, "仅待审核申请可以驳回");

        Long userId = ((Number) apply.get("userId")).longValue();
        java.math.BigDecimal amount = toAmount(apply.get("amount"));
        int restored = jdbc.update(
                "UPDATE sys_income_account SET available_amount = available_amount + ?, "
                        + "frozen_amount = CASE WHEN frozen_amount >= ? THEN frozen_amount - ? ELSE frozen_amount END, "
                        + "version = version + 1, updated_at = NOW() WHERE user_id = ?",
                amount, amount, amount, userId);
        if (restored != 1) {
            throw new IllegalStateException("驳回后退回余额失败，请检查收益账户");
        }
    }

    private void releaseLegacyFrozen(Map<String, Object> apply) {
        Long userId = ((Number) apply.get("userId")).longValue();
        java.math.BigDecimal amount = toAmount(apply.get("amount"));
        jdbc.update(
                "UPDATE sys_income_account SET frozen_amount = frozen_amount - ?, version = version + 1, updated_at = NOW() "
                        + "WHERE user_id = ? AND frozen_amount >= ?",
                amount, userId, amount);
    }

    private static java.math.BigDecimal toAmount(Object amountObj) {
        return amountObj instanceof java.math.BigDecimal bd
                ? bd : new java.math.BigDecimal(amountObj.toString());
    }

    private static void requireChanged(int rows, String message) {
        if (rows != 1) throw new IllegalStateException(message);
    }

    private static Filter filter(String status, String keyword) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            String value = status.trim();
            if (!STATUSES.contains(value)) throw new IllegalArgumentException("提现状态无效");
            sql.append(" AND w.status = ?");
            args.add(value);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            if (value.length() > 100) value = value.substring(0, 100);
            sql.append(" AND (w.withdraw_no LIKE ? OR u.user_name LIKE ? OR u.nick_name LIKE ?)");
            String match = "%" + value + "%";
            args.add(match);
            args.add(match);
            args.add(match);
        }
        return new Filter(sql.toString(), args);
    }

    private record Filter(String sql, List<Object> args) { }
}
