package com.ruoyi.web.service.pc;

import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.Seq;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ContractManageService {
    private static final String CONTRACT_SELECT = "SELECT c.contract_id AS contractId, c.contract_no AS contractNo, "
            + "c.contract_type AS contractType, c.template_id AS templateId, c.amount AS amount, "
            + "c.order_id AS orderId, o.order_no AS orderNo, c.buyer_id AS buyerId, ub.nick_name AS buyerName, "
            + "c.seller_id AS sellerId, us.nick_name AS sellerName, c.buyer_sign_status AS buyerSignStatus, "
            + "c.seller_sign_status AS sellerSignStatus, c.buyer_sign_time AS buyerSignTime, "
            + "c.seller_sign_time AS sellerSignTime, c.status AS status, c.contract_content AS content, "
            + "w.title AS workTitle, DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
            + "FROM sys_contract c JOIN sys_order o ON o.order_id = c.order_id "
            + "LEFT JOIN sys_user ub ON ub.user_id = c.buyer_id LEFT JOIN sys_user us ON us.user_id = c.seller_id "
            + "LEFT JOIN sys_work w ON w.work_id = o.work_id ";

    private final JdbcTemplate jdbc;

    public ContractManageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count(String status, String keyword) {
        Filter filter = filter(status, keyword);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_contract c JOIN sys_order o ON o.order_id = c.order_id"
                + filter.sql, Long.class, filter.args.toArray());
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> list(String status, String keyword, int pageNum, int pageSize) {
        Filter filter = filter(status, keyword);
        List<Object> args = new java.util.ArrayList<>(filter.args);
        args.add(pageSize);
        args.add((long) (pageNum - 1) * pageSize);
        return jdbc.queryForList(CONTRACT_SELECT + filter.sql + " ORDER BY c.created_at DESC LIMIT ? OFFSET ?", args.toArray());
    }

    public Map<String, Object> get(Long contractId) {
        try {
            return jdbc.queryForMap(CONTRACT_SELECT + " WHERE c.contract_id = ?", contractId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("合同记录不存在");
        }
    }

    public List<Map<String, Object>> availableOrders() {
        return jdbc.queryForList("SELECT o.order_id AS orderId, o.order_no AS orderNo, o.total_amount AS amount, "
                + "o.buyer_id AS buyerId, ub.nick_name AS buyerName, o.seller_id AS sellerId, "
                + "us.nick_name AS sellerName, w.title AS workTitle, o.license_type AS licenseType "
                + "FROM sys_order o LEFT JOIN sys_user ub ON ub.user_id = o.buyer_id "
                + "LEFT JOIN sys_user us ON us.user_id = o.seller_id LEFT JOIN sys_work w ON w.work_id = o.work_id "
                + "WHERE o.status = 'confirmed' AND o.contract_id IS NULL "
                + "AND NOT EXISTS (SELECT 1 FROM sys_contract c WHERE c.order_id = o.order_id AND c.status <> 'cancelled') "
                + "ORDER BY o.created_at DESC");
    }

    @Transactional
    public Map<String, Object> generate(Long orderId, String templateId) {
        if (!"TPL_COPYRIGHT_TRANSFER".equals(templateId) && !"TPL_COPYRIGHT_LICENSE".equals(templateId)) {
            throw new IllegalArgumentException("合同模板无效");
        }
        Map<String, Object> order;
        try {
            order = jdbc.queryForMap("SELECT o.order_id AS orderId, o.order_no AS orderNo, o.total_amount AS amount, "
                    + "o.buyer_id AS buyerId, ub.nick_name AS buyerName, o.seller_id AS sellerId, "
                    + "us.nick_name AS sellerName, o.status AS status, o.contract_id AS contractId, "
                    + "o.license_type AS licenseType, w.title AS workTitle "
                    + "FROM sys_order o LEFT JOIN sys_user ub ON ub.user_id = o.buyer_id "
                    + "LEFT JOIN sys_user us ON us.user_id = o.seller_id LEFT JOIN sys_work w ON w.work_id = o.work_id "
                    + "WHERE o.order_id = ? FOR UPDATE", orderId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("关联订单不存在");
        }
        String oldStatus = String.valueOf(order.get("status"));
        if (!"confirmed".equals(oldStatus) || order.get("contractId") != null) {
            throw new IllegalStateException("该订单当前状态不能生成合同");
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM sys_contract WHERE order_id = ? AND status <> 'cancelled'", Integer.class, orderId);
        if (existing != null && existing > 0) {
            throw new IllegalStateException("该订单已存在合同");
        }

        String contractNo = "CT" + Seq.getId();
        String contractType = "TPL_COPYRIGHT_TRANSFER".equals(templateId) ? "transfer" : "license";
        String html = contractHtml(contractNo, order, templateId);
        jdbc.update("INSERT INTO sys_contract (contract_no, contract_type, amount, order_id, buyer_id, seller_id, "
                        + "template_id, contract_content, contract_url, buyer_sign_status, seller_sign_status, status, create_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, '', 0, 0, 'pending', ?)",
                contractNo, contractType, order.get("amount"), orderId, order.get("buyerId"), order.get("sellerId"),
                templateId, html, SecurityUtils.getUsername());
        Long contractId = jdbc.queryForObject("SELECT contract_id FROM sys_contract WHERE contract_no = ?", Long.class, contractNo);
        int updated = jdbc.update("UPDATE sys_order SET contract_id = ?, update_by = ?, update_time = NOW() "
                        + "WHERE order_id = ? AND contract_id IS NULL AND status = 'confirmed'",
                contractId, SecurityUtils.getUsername(), orderId);
        if (updated != 1) {
            throw new IllegalStateException("订单状态已变化，请刷新后重试");
        }
        logOrderStatus(orderId, oldStatus, "GENERATED", "生成合同");
        logContractEvent(contractId, "", "pending", "合同生成");
        return Map.of("contractId", contractId, "contractNo", contractNo);
    }

    @Transactional
    public void sign(Long contractId, String party) {
        if (!"buyer".equals(party) && !"seller".equals(party)) {
            throw new IllegalArgumentException("签署方无效");
        }
        Map<String, Object> contract = lockContract(contractId);
        String currentStatus = String.valueOf(contract.get("status"));
        if (!List.of("pending", "partial_signed").contains(currentStatus)) {
            throw new IllegalStateException("当前合同状态不可登记签署");
        }
        String signedColumn = "buyer".equals(party) ? "buyer_sign_status" : "seller_sign_status";
        String timeColumn = "buyer".equals(party) ? "buyer_sign_time" : "seller_sign_time";
        if (number(contract.get("buyerSignStatus")) == 1 && "buyer".equals(party)
                || number(contract.get("sellerSignStatus")) == 1 && "seller".equals(party)) {
            throw new IllegalStateException("该方已登记签署");
        }
        boolean buyerSigned = number(contract.get("buyerSignStatus")) == 1 || "buyer".equals(party);
        boolean sellerSigned = number(contract.get("sellerSignStatus")) == 1 || "seller".equals(party);
        String nextStatus = buyerSigned && sellerSigned ? "completed" : "partial_signed";
        int changed = jdbc.update("UPDATE sys_contract SET " + signedColumn + " = 1, " + timeColumn
                        + " = NOW(), status = ?, update_by = ?, update_time = NOW() "
                        + "WHERE contract_id = ? AND status IN ('pending', 'partial_signed') AND " + signedColumn + " = 0",
                nextStatus, SecurityUtils.getUsername(), contractId);
        requireChanged(changed, "合同状态已变化，请刷新后重试");
        logContractEvent(contractId, currentStatus, nextStatus, "登记" + ("buyer".equals(party) ? "买方" : "卖方") + "签署");
    }

    @Transactional
    public void archive(Long contractId) {
        int updated = jdbc.update("UPDATE sys_contract SET status = 'archived', update_by = ?, update_time = NOW() "
                + "WHERE contract_id = ? AND status = 'completed'", SecurityUtils.getUsername(), contractId);
        requireChanged(updated, "仅已完成合同可以归档");
        logContractEvent(contractId, "completed", "archived", "合同归档");
    }

    @Transactional
    public void cancel(Long contractId) {
        Map<String, Object> contract = lockContract(contractId);
        String status = String.valueOf(contract.get("status"));
        if (!List.of("pending", "partial_signed").contains(status)
                || number(contract.get("buyerSignStatus")) != 0 || number(contract.get("sellerSignStatus")) != 0) {
            throw new IllegalStateException("仅未签署的待签合同可以作废");
        }
        int updated = jdbc.update("UPDATE sys_contract SET status = 'cancelled', update_by = ?, update_time = NOW() "
                + "WHERE contract_id = ? AND status IN ('pending', 'partial_signed')", SecurityUtils.getUsername(), contractId);
        requireChanged(updated, "合同状态已变化，请刷新后重试");
        Long orderId = ((Number) contract.get("orderId")).longValue();
        int orderUpdated = jdbc.update("UPDATE sys_order SET contract_id = NULL, update_by = ?, update_time = NOW() "
                + "WHERE order_id = ? AND contract_id = ?", SecurityUtils.getUsername(), orderId, contractId);
        requireChanged(orderUpdated, "关联订单不存在或合同关联已变化");
        logContractEvent(contractId, status, "cancelled", "合同作废");
    }

    @Transactional
    public void update(Long contractId, String templateId, Double amount) {
        if (!templateId.equals("TPL_COPYRIGHT_TRANSFER") && !templateId.equals("TPL_COPYRIGHT_LICENSE")) {
            throw new IllegalArgumentException("合同模板无效");
        }
        
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("合同金额必须大于0");
        }
        
        Map<String, Object> contract = lockContract(contractId);
        String status = String.valueOf(contract.get("status"));
        
        if (!status.equals("pending")) {
            throw new IllegalStateException("仅待签署状态的合同可以编辑");
        }
        
        if (number(contract.get("buyerSignStatus")) != 0 || number(contract.get("sellerSignStatus")) != 0) {
            throw new IllegalStateException("已有签署记录的合同不可编辑");
        }
        
        Long orderId = ((Number) contract.get("orderId")).longValue();
        Map<String, Object> order;
        try {
            order = jdbc.queryForMap("SELECT o.order_id AS orderId, o.order_no AS orderNo, o.total_amount AS amount, "
                    + "o.buyer_id AS buyerId, ub.nick_name AS buyerName, o.seller_id AS sellerId, "
                    + "us.nick_name AS sellerName, o.license_type AS licenseType, w.title AS workTitle "
                    + "FROM sys_order o LEFT JOIN sys_user ub ON ub.user_id = o.buyer_id "
                    + "LEFT JOIN sys_user us ON us.user_id = o.seller_id LEFT JOIN sys_work w ON w.work_id = o.work_id "
                    + "WHERE o.order_id = ?", orderId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("关联订单不存在");
        }
        
        order.put("amount", amount);
        
        String contractNo = String.valueOf(jdbc.queryForObject(
                "SELECT contract_no FROM sys_contract WHERE contract_id = ?", String.class, contractId));
        String contractType = templateId.equals("TPL_COPYRIGHT_TRANSFER") ? "transfer" : "license";
        String html = contractHtml(contractNo, order, templateId);
        
        int updated = jdbc.update("UPDATE sys_contract SET contract_type = ?, template_id = ?, amount = ?, contract_content = ?, "
                + "update_by = ?, update_time = NOW() WHERE contract_id = ? AND status = 'pending'",
                contractType, templateId, amount, html, SecurityUtils.getUsername(), contractId);
        requireChanged(updated, "合同状态已变化或不满足编辑条件");
        logContractEvent(contractId, status, status, "合同编辑");
    }

    private Map<String, Object> lockContract(Long contractId) {
        try {
            return jdbc.queryForMap("SELECT contract_id AS contractId, order_id AS orderId, status, "
                    + "buyer_sign_status AS buyerSignStatus, seller_sign_status AS sellerSignStatus "
                    + "FROM sys_contract WHERE contract_id = ? FOR UPDATE", contractId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("合同记录不存在");
        }
    }

    private void logOrderStatus(Long orderId, String from, String to, String remark) {
        jdbc.update("INSERT INTO sys_order_status_log (order_id, from_status, to_status, operator_id, operator_role, "
                        + "remark, created_at, create_by, create_time) VALUES (?, ?, ?, ?, 'admin', ?, NOW(), ?, NOW())",
                orderId, from, to, SecurityUtils.getUserId(), remark, SecurityUtils.getUsername());
    }

    private void logContractEvent(Long contractId, String fromStatus, String toStatus, String remark) {
        String payload = "{\"fromStatus\":\"" + escapeJson(fromStatus) + "\",\"toStatus\":\""
                + escapeJson(toStatus) + "\",\"remark\":\"" + escapeJson(remark) + "\"}";
        jdbc.update("INSERT INTO sys_contract_event (event_no, contract_id, provider, event_type, event_payload, "
                        + "process_status, retry_count, event_time, processed_at, created_at, create_by, create_time) "
                        + "VALUES (?, ?, 'admin', 'STATUS_CHANGED', ?, 'processed', 0, NOW(), NOW(), NOW(), ?, NOW())",
                "CE" + Seq.getId(), contractId, payload, SecurityUtils.getUsername());
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static void requireChanged(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }

    private static Filter filter(String status, String keyword) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND c.status = ?");
            args.add(status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (c.contract_no LIKE ? OR o.order_no LIKE ?)");
            String match = "%" + keyword.trim() + "%";
            args.add(match);
            args.add(match);
        }
        return new Filter(sql.toString(), args);
    }

    private record Filter(String sql, List<Object> args) { }

    private static String contractHtml(String contractNo, Map<String, Object> order, String templateId) {
        String title = "TPL_COPYRIGHT_TRANSFER".equals(templateId) ? "版权转让合同" : "版权授权合同";
        boolean isTransfer = "TPL_COPYRIGHT_TRANSFER".equals(templateId);
        
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\" />"
                + "<title>" + title + "</title>"
                + "<style>"
                + "body { font-family: 'ContractCjk', SimSun, serif; font-size: 14px; line-height: 1.8; margin: 40px; }"
                + "h1 { text-align: center; font-size: 24px; margin-bottom: 30px; font-weight: bold; }"
                + ".contract-no { text-align: center; font-size: 12px; color: #666; margin-bottom: 20px; }"
                + ".section { margin-bottom: 20px; }"
                + ".section-title { font-weight: bold; font-size: 16px; margin-bottom: 10px; }"
                + ".info-row { margin-bottom: 8px; }"
                + ".label { display: inline-block; width: 120px; font-weight: bold; }"
                + ".content { margin: 15px 0; text-indent: 2em; text-align: justify; }"
                + ".signature { margin-top: 60px; }"
                + ".signature-row { margin-top: 40px; display: flex; justify-content: space-between; }"
                + ".signature-box { width: 45%; }"
                + "</style></head><body>"
                + "<h1>" + title + "</h1>"
                + "<div class=\"contract-no\">合同编号：" + escape(contractNo) + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">基本信息</div>"
                + "<div class=\"info-row\"><span class=\"label\">订单编号：</span>" + escape(order.get("orderNo")) + "</div>"
                + "<div class=\"info-row\"><span class=\"label\">作品名称：</span>" + escape(order.get("workTitle")) + "</div>"
                + "<div class=\"info-row\"><span class=\"label\">甲方（买方）：</span>" + escape(order.get("buyerName")) + "</div>"
                + "<div class=\"info-row\"><span class=\"label\">乙方（卖方）：</span>" + escape(order.get("sellerName")) + "</div>"
                + "<div class=\"info-row\"><span class=\"label\">合同金额：</span>人民币 " + escape(order.get("amount")) + " 元</div>"
                + "<div class=\"info-row\"><span class=\"label\">许可类型：</span>" + escape(order.get("licenseType")) + "</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第一条 合同标的</div>"
                + "<div class=\"content\">甲乙双方经友好协商，就作品《" + escape(order.get("workTitle")) + "》的版权"
                + (isTransfer ? "转让" : "授权使用") + "事宜达成如下协议。</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第二条 " + (isTransfer ? "转让内容" : "授权范围") + "</div>"
                + "<div class=\"content\">"
                + (isTransfer 
                    ? "乙方同意将作品的全部著作权（包括但不限于复制权、发行权、信息网络传播权、改编权等）转让给甲方。转让后，甲方享有完整的著作权，乙方不再保留任何权利。"
                    : "乙方授权甲方在约定范围内使用作品，授权类型为：" + escape(order.get("licenseType")) + "。甲方应在授权范围内使用作品，不得超范围使用或转授权给第三方。")
                + "</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第三条 合同金额及支付</div>"
                + "<div class=\"content\">甲方应向乙方支付" + (isTransfer ? "转让" : "授权") + "费用人民币 " 
                + escape(order.get("amount")) + " 元。该费用已通过平台完成支付，合同签署完成后将按约定结算给乙方。</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第四条 权利与义务</div>"
                + "<div class=\"content\">1. 乙方保证对作品享有完整的著作权，不存在侵犯第三方权益的情形。</div>"
                + "<div class=\"content\">2. 甲方应按约定支付费用，并在授权范围内合法使用作品。</div>"
                + "<div class=\"content\">3. 双方应遵守国家相关法律法规，履行合同义务。</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第五条 违约责任</div>"
                + "<div class=\"content\">任何一方违反本合同约定的，应承担违约责任，赔偿对方因此遭受的损失。</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第六条 争议解决</div>"
                + "<div class=\"content\">因本合同引起的争议，双方应友好协商解决；协商不成的，可向平台所在地人民法院提起诉讼。</div>"
                + "</div>"
                + "<div class=\"signature\">"
                + "<div class=\"signature-row\">"
                + "<div class=\"signature-box\"><strong>甲方（买方）：</strong>" + escape(order.get("buyerName")) + "<br/><br/>签署时间：___________</div>"
                + "<div class=\"signature-box\"><strong>乙方（卖方）：</strong>" + escape(order.get("sellerName")) + "<br/><br/>签署时间：___________</div>"
                + "</div>"
                + "</div>"
                + "</body></html>";
    }

    private static String escape(Object value) {
        if (value == null) return "—";
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
