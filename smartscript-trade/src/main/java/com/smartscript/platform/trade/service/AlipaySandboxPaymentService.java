package com.smartscript.platform.trade.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.smartscript.platform.trade.domain.SysOrder;

/** 官方支付宝沙箱：仅对独立的 _sandbox 数据库开放，查询验证后推进测试订单。 */
@Service
public class AlipaySandboxPaymentService {
    static final String GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private final JdbcTemplate jdbc;
    private final TradeOrderService orders;
    private final boolean enabled;
    private final String appId;
    private final String privateKey;
    private final String publicKey;
    @Value("${APP_ALIPAY_SANDBOX_NOTIFY_URL:}")
    private String notifyUrl;

    public AlipaySandboxPaymentService(JdbcTemplate jdbc, TradeOrderService orders,
            @Value("${APP_ALIPAY_SANDBOX_ENABLED:false}") boolean enabled,
            @Value("${APP_ALIPAY_SANDBOX_APP_ID:}") String appId,
            @Value("${APP_ALIPAY_SANDBOX_PRIVATE_KEY:}") String privateKey,
            @Value("${APP_ALIPAY_SANDBOX_PUBLIC_KEY:}") String publicKey) {
        this.jdbc = jdbc; this.orders = orders; this.enabled = enabled;
        this.appId = appId; this.privateKey = privateKey; this.publicKey = publicKey;
    }

    private void requireSandbox() {
        if (!enabled || appId.isBlank() || privateKey.isBlank() || publicKey.isBlank())
            throw new IllegalStateException("支付宝沙箱尚未配置");
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (database == null || !database.toLowerCase(java.util.Locale.ROOT).endsWith("_sandbox"))
            throw new IllegalStateException("支付宝沙箱仅允许使用独立测试数据库");
    }

    private SysOrder buyerOrder(Long orderId, Long buyerId) {
        SysOrder order = orders.selectOrderById(orderId);
        if (order == null || buyerId == null || !buyerId.equals(order.getBuyerId()))
            throw new IllegalArgumentException("订单不存在或无权付款");
        return order;
    }

    AlipayClient client() {
        return new DefaultAlipayClient(GATEWAY, appId, privateKey, "json", "UTF-8", publicKey, "RSA2");
    }

    @Transactional
    public Map<String, Object> create(Long orderId, Long buyerId) {
        requireSandbox();
        SysOrder order = buyerOrder(orderId, buyerId);
        if (!TradeOrderService.ORDER_STATUS_PAYING.equals(order.getStatus()))
            throw new IllegalStateException("当前订单状态不可付款");
        BigDecimal amount = order.getTotalAmount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2)
            throw new IllegalStateException("订单金额无效");
        String candidate = "SSSB" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sandbox_alipay_payment(order_id,out_trade_no,buyer_id,amount) VALUES(?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE order_id=order_id", orderId, candidate, buyerId, amount);
        Map<String, Object> row = jdbc.queryForMap("SELECT out_trade_no,buyer_id,amount,status FROM sandbox_alipay_payment WHERE order_id=? FOR UPDATE", orderId);
        if (((Number) row.get("buyer_id")).longValue() != buyerId || amount.compareTo((BigDecimal) row.get("amount")) != 0)
            throw new IllegalStateException("支付记录与订单不一致");
        String outTradeNo = (String) row.get("out_trade_no");
        if ("CLOSED".equals(row.get("status"))) {
            jdbc.update("UPDATE sandbox_alipay_payment SET out_trade_no=?,status='PENDING' WHERE order_id=? AND status='CLOSED'", candidate, orderId);
            outTradeNo = candidate;
        } else if (!"PENDING".equals(row.get("status"))) throw new IllegalStateException("订单已支付");
        try {
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(outTradeNo); model.setTotalAmount(amount.toPlainString());
            model.setSubject("剧云策沙箱订单 " + orderId);
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest(); request.setBizModel(model);
            if (notifyUrl != null && !notifyUrl.isBlank()) {
                if (!notifyUrl.startsWith("https://")) throw new IllegalStateException("沙箱通知地址必须使用 HTTPS");
                request.setNotifyUrl(notifyUrl);
            }
            AlipayTradePrecreateResponse response = client().execute(request);
            if (!response.isSuccess() || !outTradeNo.equals(response.getOutTradeNo()) || response.getQrCode() == null || response.getQrCode().isBlank())
                throw new IllegalStateException("支付宝沙箱创建支付失败，请检查沙箱配置");
            return Map.of("qrCode", response.getQrCode(), "sandbox", true);
        } catch (AlipayApiException e) { throw new IllegalStateException("支付宝沙箱服务暂不可用"); }
    }

    /** 验证支付宝通知后，仍以支付宝主动查单结果为准。 */
    public boolean processNotification(Map<String, String> params) {
        requireSandbox();
        try {
            if (!appId.equals(params.get("app_id")) || !"RSA2".equals(params.get("sign_type")) ||
                    !com.alipay.api.internal.util.AlipaySignature.rsaCheckV1(new java.util.HashMap<>(params), publicKey, "UTF-8", "RSA2")) return false;
            String status = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) return true;
            String outTradeNo = params.get("out_trade_no");
            if (outTradeNo == null || outTradeNo.isBlank()) return false;
            List<Map<String,Object>> rows = jdbc.queryForList("SELECT order_id,buyer_id FROM sandbox_alipay_payment WHERE out_trade_no=?", outTradeNo);
            if (rows.size() != 1) return false;
            Map<String,Object> row = rows.get(0);
            return Boolean.TRUE.equals(self.getObject().confirm(((Number) row.get("order_id")).longValue(), ((Number) row.get("buyer_id")).longValue()).get("paid"));
        } catch (Exception e) { return false; }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<AlipaySandboxPaymentService> self;

    @Transactional
    public Map<String, Object> confirm(Long orderId, Long buyerId) {
        requireSandbox();
        SysOrder order = buyerOrder(orderId, buyerId);
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT out_trade_no,buyer_id,amount,status FROM sandbox_alipay_payment WHERE order_id=? FOR UPDATE", orderId);
        if (rows.isEmpty()) throw new IllegalStateException("请先发起沙箱支付");
        Map<String,Object> row = rows.get(0);
        if (((Number) row.get("buyer_id")).longValue() != buyerId || order.getTotalAmount() == null
                || order.getTotalAmount().compareTo((BigDecimal) row.get("amount")) != 0)
            throw new IllegalStateException("支付记录与订单不一致");
        if ("PAID".equals(row.get("status"))) return Map.of("paid", true, "sandbox", true);
        String outTradeNo = (String) row.get("out_trade_no");
        try {
            AlipayTradeQueryModel model = new AlipayTradeQueryModel(); model.setOutTradeNo(outTradeNo);
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest(); request.setBizModel(model);
            AlipayTradeQueryResponse response = client().execute(request);
            if (!response.isSuccess()) {
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) return Map.of("paid", false, "sandbox", true);
                throw new IllegalStateException("支付宝沙箱查询失败，请检查配置后重试");
            }
            String status = response.getTradeStatus();
            if ("TRADE_CLOSED".equals(status)) {
                if (!outTradeNo.equals(response.getOutTradeNo())) throw new IllegalStateException("支付宝返回的订单不匹配");
                jdbc.update("UPDATE sandbox_alipay_payment SET status='CLOSED' WHERE order_id=? AND status='PENDING'", orderId);
                return Map.of("paid", false, "closed", true, "sandbox", true);
            }
            if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status))
                return Map.of("paid", false, "sandbox", true);
            if (!outTradeNo.equals(response.getOutTradeNo()) || response.getTradeNo() == null || response.getTradeNo().isBlank()
                    || response.getTotalAmount() == null || order.getTotalAmount().compareTo(new BigDecimal(response.getTotalAmount())) != 0)
                throw new IllegalStateException("支付宝返回的订单或金额不匹配");
            orders.payOrder(orderId, buyerId);
            jdbc.update("UPDATE sandbox_alipay_payment SET status='PAID',trade_no=? WHERE order_id=? AND status='PENDING'", response.getTradeNo(), orderId);
            return Map.of("paid", true, "sandbox", true);
        } catch (AlipayApiException e) { throw new IllegalStateException("支付宝沙箱服务暂不可用"); }
    }
}
