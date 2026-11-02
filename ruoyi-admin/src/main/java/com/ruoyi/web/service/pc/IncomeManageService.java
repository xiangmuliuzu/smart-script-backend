package com.ruoyi.web.service.pc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人收益管理（只读）：有 sys_income_account 的用户 + sys_bill_record 流水。
 * 契约：docs/contracts/2026-11-02-admin-personal-income.md
 */
@Service
public class IncomeManageService {

    private static final String ACCOUNT_SELECT =
            "SELECT a.account_id AS accountId, a.user_id AS userId, "
                    + "u.user_name AS userName, "
                    + "COALESCE(u.nick_name, u.user_name, CONCAT('用户', a.user_id)) AS nickName, "
                    + "u.phonenumber AS phonenumber, "
                    + "a.available_amount AS availableAmount, "
                    + "a.frozen_amount AS frozenAmount, "
                    + "a.settled_amount AS settledAmount, "
                    + "DATE_FORMAT(COALESCE(a.updated_at, a.update_time), '%Y-%m-%d %H:%i:%s') AS updatedAt "
                    + "FROM sys_income_account a "
                    + "LEFT JOIN sys_user u ON u.user_id = a.user_id ";

    private static final String BILL_SELECT =
            "SELECT b.bill_id AS billId, b.bill_no AS billNo, b.user_id AS userId, "
                    + "b.bill_type AS billType, b.amount AS amount, b.balance_after AS balanceAfter, "
                    + "b.related_type AS relatedType, b.related_id AS relatedId, b.remark AS remark, "
                    + "DATE_FORMAT(COALESCE(b.created_at, b.create_time), '%Y-%m-%d %H:%i:%s') AS createdAt "
                    + "FROM sys_bill_record b ";

    private final JdbcTemplate jdbc;

    public IncomeManageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listAccounts(String keyword, int page, int size) {
        StringBuilder sql = new StringBuilder(ACCOUNT_SELECT + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendAccountKeyword(sql, args, keyword);
        sql.append(" ORDER BY COALESCE(a.updated_at, a.update_time, a.created_at) DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) (page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            enrichAccountRow(row);
        }
        return rows;
    }

    public long countAccounts(String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM sys_income_account a LEFT JOIN sys_user u ON u.user_id = a.user_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendAccountKeyword(sql, args, keyword);
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public Map<String, Object> getAccount(Long userId) {
        requireUserId(userId);
        List<Map<String, Object>> rows = jdbc.queryForList(ACCOUNT_SELECT + " WHERE a.user_id = ? LIMIT 1", userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("该用户没有收益账户");
        }
        Map<String, Object> detail = new HashMap<>(rows.get(0));
        enrichAccountRow(detail);
        return detail;
    }

    public List<Map<String, Object>> listBills(Long userId, String billType, String keyword, int page, int size) {
        requireUserId(userId);
        ensureAccountExists(userId);
        StringBuilder sql = new StringBuilder(BILL_SELECT + " WHERE b.user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        appendBillFilters(sql, args, billType, keyword);
        sql.append(" ORDER BY COALESCE(b.created_at, b.create_time) DESC, b.bill_id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) (page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            row.put("incomeSource", billSourceLabel(str(row.get("billType"))));
        }
        return rows;
    }

    public long countBills(Long userId, String billType, String keyword) {
        requireUserId(userId);
        ensureAccountExists(userId);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sys_bill_record b WHERE b.user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        appendBillFilters(sql, args, billType, keyword);
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private void enrichAccountRow(Map<String, Object> row) {
        Long userId = toLong(row.get("userId"));
        row.put("phonenumber", maskPhone(str(row.get("phonenumber"))));
        row.put("pendingSettleAmount", queryPendingSettleAmount(userId));
        // 与 App 收益摘要一致：冻结=未打款提现；已提现=已打款。不读账户遗留 frozen_amount。
        row.put("frozenAmount", queryInFlightWithdrawAmount(userId));
        row.put("withdrawnAmount", queryWithdrawnAmount(userId));
    }

    private void appendAccountKeyword(StringBuilder sql, List<Object> args, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String kw = keyword.trim();
        sql.append(" AND (u.nick_name LIKE ? OR u.user_name LIKE ? OR u.phonenumber LIKE ?");
        args.add("%" + kw + "%");
        args.add("%" + kw + "%");
        args.add("%" + kw + "%");
        if (kw.matches("\\d+")) {
            sql.append(" OR a.user_id = ?");
            args.add(Long.parseLong(kw));
        }
        sql.append(")");
    }

    private void appendBillFilters(StringBuilder sql, List<Object> args, String billType, String keyword) {
        if (StringUtils.hasText(billType)) {
            sql.append(" AND b.bill_type = ?");
            args.add(billType.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            sql.append(" AND (b.bill_no LIKE ? OR IFNULL(b.remark, '') LIKE ?");
            args.add("%" + kw + "%");
            args.add("%" + kw + "%");
            if (kw.matches("\\d+")) {
                sql.append(" OR b.related_id = ? OR b.bill_id = ?");
                args.add(Long.parseLong(kw));
                args.add(Long.parseLong(kw));
            }
            sql.append(")");
        }
    }

    private Object queryPendingSettleAmount(Long userId) {
        if (userId == null) {
            return 0;
        }
        try {
            return jdbc.queryForObject(
                    "SELECT IFNULL(SUM(seller_amount), 0) FROM sys_income_settle WHERE user_id = ? AND status = 'pending'",
                    Object.class, userId);
        } catch (Exception e) {
            return 0;
        }
    }

    private Object queryInFlightWithdrawAmount(Long userId) {
        if (userId == null) {
            return 0;
        }
        try {
            return jdbc.queryForObject(
                    "SELECT IFNULL(SUM(amount), 0) FROM sys_withdraw_apply "
                            + "WHERE user_id = ? AND status IN ('pending','approved','frozen') "
                            + "AND pay_time IS NULL",
                    Object.class, userId);
        } catch (Exception e) {
            return 0;
        }
    }

    private Object queryWithdrawnAmount(Long userId) {
        if (userId == null) {
            return 0;
        }
        try {
            return jdbc.queryForObject(
                    "SELECT IFNULL(SUM(amount), 0) FROM sys_withdraw_apply "
                            + "WHERE user_id = ? AND (status = 'paid' OR pay_time IS NOT NULL)",
                    Object.class, userId);
        } catch (Exception e) {
            return 0;
        }
    }

    private void ensureAccountExists(Long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_income_account WHERE user_id = ?", Long.class, userId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("该用户没有收益账户");
        }
    }

    private static void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
    }

    private static String billSourceLabel(String billType) {
        if (billType == null) {
            return "未知";
        }
        return switch (billType) {
            case "income" -> "订单收入";
            case "withdraw" -> "提现支出";
            case "freeze" -> "资金冻结";
            case "settle" -> "分成结算";
            default -> billType;
        };
    }

    private static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
