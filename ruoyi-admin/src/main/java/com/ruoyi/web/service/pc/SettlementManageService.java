package com.ruoyi.web.service.pc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 结算管理：与 App 同源 sys_income_settle。
 * 订单完成后自动建单；作者确认入账 / 提出异议；管理员仅处理异议并回退待确认。
 */
@Service
public class SettlementManageService {

    private static final String SETTLE_SELECT =
            "SELECT s.settle_id AS settleId, s.settle_id AS settlementId, "
                    + "s.settle_no AS settleNo, s.settle_no AS settlementNo, "
                    + "s.user_id AS userId, s.user_id AS authorId, "
                    + "COALESCE(u.nick_name, u.user_name, CONCAT('用户', s.user_id)) AS authorName, "
                    + "s.order_id AS orderId, s.order_no AS orderNo, s.work_title AS workTitle, "
                    + "s.total_amount AS totalAmount, s.platform_fee AS platformFee, "
                    + "s.platform_fee AS platformAmount, "
                    + "s.split_rate AS splitRate, s.seller_amount AS sellerAmount, "
                    + "s.seller_amount AS authorAmount, "
                    + "s.status AS status, s.dispute_reason AS disputeReason, "
                    + "s.dispute_reason AS abnormalReason, "
                    + "s.bill_id AS billId, "
                    + "1 AS orderCount, "
                    + "DATE_FORMAT(s.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirmTime, "
                    + "DATE_FORMAT(s.confirm_time, '%Y-%m-%d %H:%i:%s') AS settlementTime, "
                    + "DATE_FORMAT(s.dispute_time, '%Y-%m-%d %H:%i:%s') AS disputeTime, "
                    + "DATE_FORMAT(s.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
                    + "FROM sys_income_settle s "
                    + "LEFT JOIN sys_user u ON u.user_id = s.user_id ";

    private final JdbcTemplate jdbc;

    public SettlementManageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String status, String startDate, String endDate, String keyword, int page, int size) {
        StringBuilder sql = new StringBuilder(SETTLE_SELECT + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, startDate, endDate, keyword);
        sql.append(" ORDER BY s.created_at DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) (page - 1) * size);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public long count(String status, String startDate, String endDate, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM sys_income_settle s LEFT JOIN sys_user u ON u.user_id = s.user_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, startDate, endDate, keyword);
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public Map<String, Object> get(Long settleId) {
        if (settleId == null || settleId <= 0) {
            throw new IllegalArgumentException("结算ID无效");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(SETTLE_SELECT + " WHERE s.settle_id = ? LIMIT 1", settleId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("结算单不存在");
        }
        Map<String, Object> detail = new HashMap<>(rows.get(0));
        List<Map<String, Object>> orders = new ArrayList<>();
        Map<String, Object> orderLine = new HashMap<>();
        orderLine.put("orderNo", detail.get("orderNo"));
        orderLine.put("workName", detail.get("workTitle"));
        orderLine.put("amount", detail.get("totalAmount"));
        orderLine.put("authorIncome", detail.get("sellerAmount"));
        orderLine.put("platformFee", detail.get("platformFee"));
        orderLine.put("splitRate", detail.get("splitRate"));
        orderLine.put("orderTime", detail.get("createdAt"));
        orders.add(orderLine);
        detail.put("orders", orders);
        Object rate = detail.get("splitRate");
        if (rate instanceof Number) {
            detail.put("platformRatio", ((Number) rate).doubleValue() * 100);
        }
        return detail;
    }

    /**
     * 处理作者异议：必须填写说明，状态回到 pending，由作者再次确认入账。
     * 处理说明追加写入 dispute_reason，作者可在 App 看到。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleAbnormal(Long settleId, String remark, Boolean markAsAbnormal) {
        if (markAsAbnormal != null && markAsAbnormal) {
            throw new IllegalStateException("已停用标记异常。异常由作者在 App 提出异议产生。");
        }
        if (settleId == null || settleId <= 0) {
            throw new IllegalArgumentException("结算ID无效");
        }
        if (remark == null || remark.trim().isEmpty()) {
            throw new IllegalArgumentException("处理说明不能为空");
        }
        String text = remark.trim();
        if (text.length() > 500) {
            throw new IllegalArgumentException("处理说明不能超过500字");
        }

        List<Map<String, Object>> current = jdbc.queryForList(
                "SELECT status, dispute_reason AS disputeReason FROM sys_income_settle WHERE settle_id = ? LIMIT 1",
                settleId);
        if (current.isEmpty()) {
            throw new IllegalArgumentException("结算单不存在");
        }
        if (!"disputed".equals(String.valueOf(current.get(0).get("status")))) {
            throw new IllegalArgumentException("结算单不存在或当前不是「有异议」状态");
        }

        Object oldReason = current.get(0).get("disputeReason");
        String merged = (oldReason == null || String.valueOf(oldReason).isBlank())
                ? ("【平台处理】" + text)
                : (String.valueOf(oldReason).trim() + "\n【平台处理】" + text);
        if (merged.length() > 500) {
            merged = merged.substring(0, 500);
        }

        int rows = jdbc.update(
                "UPDATE sys_income_settle SET status = 'pending', dispute_reason = ?, updated_at = NOW() "
                        + "WHERE settle_id = ? AND status = 'disputed'",
                merged, settleId);
        if (rows == 0) {
            throw new IllegalArgumentException("结算单状态已变化，请刷新后重试");
        }
    }

    private void appendFilters(StringBuilder sql, List<Object> args, String status, String startDate, String endDate, String keyword) {
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            args.add(status.trim());
        }
        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND DATE(s.created_at) >= ?");
            args.add(startDate.trim().length() >= 10 ? startDate.trim().substring(0, 10) : startDate.trim());
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND DATE(s.created_at) <= ?");
            args.add(endDate.trim().length() >= 10 ? endDate.trim().substring(0, 10) : endDate.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            sql.append(" AND (s.settle_no LIKE ? OR s.order_no LIKE ? OR s.work_title LIKE ? OR u.nick_name LIKE ? OR u.user_name LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }
}
