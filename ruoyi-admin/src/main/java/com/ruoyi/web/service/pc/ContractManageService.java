package com.ruoyi.web.service.pc;

import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.service.TradeContractService;
import com.smartscript.platform.trade.support.ContractHtmlSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ContractManageService {
    private static final Logger log = LoggerFactory.getLogger(ContractManageService.class);

    private static final String CONTRACT_SELECT = "SELECT c.contract_id AS contractId, c.contract_no AS contractNo, "
            + "c.contract_type AS contractType, c.template_id AS templateId, c.amount AS amount, "
            + "c.order_id AS orderId, o.order_no AS orderNo, c.buyer_id AS buyerId, ub.nick_name AS buyerName, "
            + "c.seller_id AS sellerId, us.nick_name AS sellerName, c.buyer_sign_status AS buyerSignStatus, "
            + "c.seller_sign_status AS sellerSignStatus, c.buyer_sign_time AS buyerSignTime, "
            + "c.seller_sign_time AS sellerSignTime, c.buyer_signature_image AS buyerSignatureImage, "
            + "c.buyer_seal_image AS buyerSealImage, c.seller_signature_image AS sellerSignatureImage, "
            + "c.seller_seal_image AS sellerSealImage, c.signed_pdf_url AS signedPdfUrl, "
            + "c.status AS status, c.contract_content AS content, "
            + "w.title AS workTitle, DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
            + "FROM sys_contract c JOIN sys_order o ON o.order_id = c.order_id "
            + "LEFT JOIN sys_user ub ON ub.user_id = c.buyer_id LEFT JOIN sys_user us ON us.user_id = c.seller_id "
            + "LEFT JOIN sys_work w ON w.work_id = o.work_id ";

    /** 列表专用：不含 contract_content 大字段，避免分页白拖几 KB～几十 KB 正文 */
    private static final String CONTRACT_LIST_SELECT = CONTRACT_SELECT.replace(", c.contract_content AS content, ", ", ");

    private final JdbcTemplate jdbc;
    private final TradeContractService tradeContractService;

    public ContractManageService(JdbcTemplate jdbc, TradeContractService tradeContractService) {
        this.jdbc = jdbc;
        this.tradeContractService = tradeContractService;
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
        return jdbc.queryForList(CONTRACT_LIST_SELECT + filter.sql + " ORDER BY c.created_at DESC LIMIT ? OFFSET ?", args.toArray());
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
        String html = ContractHtmlSupport.buildContractHtml(contractNo,
                String.valueOf(order.get("orderNo")), String.valueOf(order.get("workTitle")),
                String.valueOf(order.get("buyerName")), String.valueOf(order.get("sellerName")),
                toAmount(order.get("amount")), String.valueOf(order.get("licenseType")), templateId);
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
        // 订单状态本身未变化（仅回填 contract_id），to_status 保持原状态，避免污染按状态统计的查询
        logOrderStatus(orderId, oldStatus, oldStatus, "生成合同");
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
        // 双方签齐后同步生成最终 PDF（与 APP 端签署行为对齐，否则 signed_pdf_url 永远为空）；
        // 生成失败不影响签署登记事务，可由管理端「重建PDF」补偿
        if ("completed".equals(nextStatus)) {
            try {
                tradeContractService.regenerateSignedPdf(contractId);
            } catch (Exception e) {
                log.error("登记双签完成后生成 PDF 失败（签署状态不受影响），contractId={}", contractId, e);
            }
        }
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
        
        // contract_no 已随 lockContract 一并锁定查出，无需二次查询
        String contractNo = String.valueOf(contract.get("contractNo"));
        String contractType = templateId.equals("TPL_COPYRIGHT_TRANSFER") ? "transfer" : "license";
        String html = ContractHtmlSupport.buildContractHtml(contractNo,
                        String.valueOf(order.get("orderNo")), String.valueOf(order.get("workTitle")),
                        String.valueOf(order.get("buyerName")), String.valueOf(order.get("sellerName")),
                        toAmount(order.get("amount")), String.valueOf(order.get("licenseType")), templateId);
                
        int updated = jdbc.update("UPDATE sys_contract SET contract_type = ?, template_id = ?, amount = ?, contract_content = ?, "
                + "update_by = ?, update_time = NOW() WHERE contract_id = ? AND status = 'pending'",
                contractType, templateId, amount, html, SecurityUtils.getUsername(), contractId);
        requireChanged(updated, "合同状态已变化或不满足编辑条件");
        logContractEvent(contractId, status, status, "合同编辑");
    }

    private Map<String, Object> lockContract(Long contractId) {
        try {
            return jdbc.queryForMap("SELECT contract_id AS contractId, contract_no AS contractNo, order_id AS orderId, status, "
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
            // 转义 LIKE 通配符，避免用户输入 % / _ 改变匹配语义
            String escaped = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String match = "%" + escaped + "%";
            args.add(match);
            args.add(match);
        }
        return new Filter(sql.toString(), args);
    }

    private record Filter(String sql, List<Object> args) { }

    private static java.math.BigDecimal toAmount(Object value) {
        if (value instanceof java.math.BigDecimal bd) {
            return bd;
        }
        return new java.math.BigDecimal(String.valueOf(value));
    }

    /**
     * 在合同 HTML 的签名区域注入双方签名/印章图片（统一委托给 trade 模块 ContractHtmlSupport）。
     * 兼容两种模板：新模板含占位注释（直接替换）；
     * 旧模板无占位符（在各方 signature-box 的“签署时间”前插入图片块）。
     */
    public String injectSignatureImages(String html, Map<String, Object> contract) {
        return ContractHtmlSupport.injectSignatures(html,
                str(contract.get("buyerSignatureImage")), str(contract.get("buyerSealImage")),
                str(contract.get("sellerSignatureImage")), str(contract.get("sellerSealImage")));
    }
    
    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
