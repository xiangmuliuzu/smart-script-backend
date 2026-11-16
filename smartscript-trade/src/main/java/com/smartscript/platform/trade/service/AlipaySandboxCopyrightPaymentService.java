package com.smartscript.platform.trade.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.smartscript.platform.content.service.ICopyrightPayOrderService;

/**
 * 支付宝沙箱 · 版权确权申请支付。
 * 支付成功后回调 content：创建申请并提交第三方。
 */
@Service
public class AlipaySandboxCopyrightPaymentService
{
    private final JdbcTemplate jdbc;
    private final AlipaySandboxPaymentService sandbox;
    private final ICopyrightPayOrderService copyrightPayOrderService;

    public AlipaySandboxCopyrightPaymentService(
            JdbcTemplate jdbc,
            AlipaySandboxPaymentService sandbox,
            ICopyrightPayOrderService copyrightPayOrderService)
    {
        this.jdbc = jdbc;
        this.sandbox = sandbox;
        this.copyrightPayOrderService = copyrightPayOrderService;
    }

    private Map<String, Object> buyerPayOrder(Long payOrderId, Long buyerId)
    {
        copyrightPayOrderService.closeExpiredOrders();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT pay_order_id,pay_no,user_id,fee_amount,status,expire_time,apply_id "
                        + "FROM sys_copyright_pay_order WHERE pay_order_id=?",
                payOrderId);
        if (rows.isEmpty())
        {
            throw new IllegalArgumentException("支付单不存在");
        }
        Map<String, Object> row = rows.get(0);
        if (buyerId == null || ((Number) row.get("user_id")).longValue() != buyerId)
        {
            throw new IllegalArgumentException("支付单不存在或无权付款");
        }
        String status = String.valueOf(row.get("status"));
        if ("paid".equals(status))
        {
            return row;
        }
        if ("closed".equals(status))
        {
            throw new IllegalStateException("支付单已关闭，请重新申请");
        }
        if (!"unpaid".equals(status) && !"paying".equals(status))
        {
            throw new IllegalStateException("当前支付单状态不可付款");
        }
        Object expire = row.get("expire_time");
        if (expire instanceof java.sql.Timestamp
                && ((java.sql.Timestamp) expire).before(new java.util.Date()))
        {
            jdbc.update("UPDATE sys_copyright_pay_order SET status='closed', update_time=NOW() "
                    + "WHERE pay_order_id=? AND status IN ('unpaid','paying')", payOrderId);
            throw new IllegalStateException("支付单已超时关闭，请重新申请");
        }
        BigDecimal amount = (BigDecimal) row.get("fee_amount");
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2)
        {
            throw new IllegalStateException("登记费金额无效");
        }
        return row;
    }

    @Transactional
    public Map<String, Object> create(Long payOrderId, Long buyerId)
    {
        sandbox.requireSandbox();
        Map<String, Object> order = buyerPayOrder(payOrderId, buyerId);
        if ("paid".equals(String.valueOf(order.get("status"))))
        {
            return Map.of("paid", true, "sandbox", true, "applyId",
                    order.get("apply_id") == null ? 0 : ((Number) order.get("apply_id")).longValue());
        }
        BigDecimal amount = (BigDecimal) order.get("fee_amount");

        String candidate = "SSCR" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sandbox_alipay_copyright_payment(pay_order_id,out_trade_no,buyer_id,amount) "
                        + "VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE pay_order_id=pay_order_id",
                payOrderId, candidate, buyerId, amount);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT out_trade_no,buyer_id,amount,status FROM sandbox_alipay_copyright_payment "
                        + "WHERE pay_order_id=? FOR UPDATE",
                payOrderId);
        if (((Number) row.get("buyer_id")).longValue() != buyerId
                || amount.compareTo((BigDecimal) row.get("amount")) != 0)
        {
            throw new IllegalStateException("支付记录与支付单不一致");
        }
        String outTradeNo = (String) row.get("out_trade_no");
        if ("CLOSED".equals(row.get("status")))
        {
            jdbc.update("UPDATE sandbox_alipay_copyright_payment SET out_trade_no=?,status='PENDING' "
                    + "WHERE pay_order_id=? AND status='CLOSED'", candidate, payOrderId);
            outTradeNo = candidate;
        }
        else if ("PAID".equals(row.get("status")))
        {
            throw new IllegalStateException("支付单已支付");
        }
        else if (!"PENDING".equals(row.get("status")))
        {
            throw new IllegalStateException("支付单状态异常");
        }

        jdbc.update("UPDATE sys_copyright_pay_order SET status='paying', update_time=NOW() "
                + "WHERE pay_order_id=? AND status IN ('unpaid','paying')", payOrderId);

        try
        {
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(outTradeNo);
            model.setTotalAmount(amount.toPlainString());
            model.setSubject("剧云策版权登记 " + order.get("pay_no"));
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setBizModel(model);
            AlipayTradePrecreateResponse response = sandbox.client().execute(request);
            if (!response.isSuccess() || !outTradeNo.equals(response.getOutTradeNo())
                    || response.getQrCode() == null || response.getQrCode().isBlank())
            {
                throw new IllegalStateException("支付宝沙箱创建支付失败，请检查沙箱配置");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("qrCode", response.getQrCode());
            result.put("sandbox", true);
            result.put("payOrderId", payOrderId);
            result.put("amount", amount);
            return result;
        }
        catch (AlipayApiException e)
        {
            throw new IllegalStateException("支付宝沙箱服务暂不可用");
        }
    }

    @Transactional
    public Map<String, Object> confirm(Long payOrderId, Long buyerId)
    {
        sandbox.requireSandbox();
        Map<String, Object> order = buyerPayOrder(payOrderId, buyerId);
        if ("paid".equals(String.valueOf(order.get("status"))) && order.get("apply_id") != null)
        {
            return Map.of("paid", true, "sandbox", true, "applyId",
                    ((Number) order.get("apply_id")).longValue());
        }
        BigDecimal amount = (BigDecimal) order.get("fee_amount");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT out_trade_no,buyer_id,amount,status FROM sandbox_alipay_copyright_payment "
                        + "WHERE pay_order_id=? FOR UPDATE",
                payOrderId);
        if (rows.isEmpty())
        {
            throw new IllegalStateException("请先发起沙箱支付");
        }
        Map<String, Object> row = rows.get(0);
        if (((Number) row.get("buyer_id")).longValue() != buyerId
                || amount.compareTo((BigDecimal) row.get("amount")) != 0)
        {
            throw new IllegalStateException("支付记录与支付单不一致");
        }
        if ("PAID".equals(row.get("status")) && order.get("apply_id") != null)
        {
            return Map.of("paid", true, "sandbox", true, "applyId",
                    ((Number) order.get("apply_id")).longValue());
        }
        String outTradeNo = (String) row.get("out_trade_no");
        try
        {
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(outTradeNo);
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            request.setBizModel(model);
            AlipayTradeQueryResponse response = sandbox.client().execute(request);
            if (!response.isSuccess())
            {
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode()))
                {
                    return Map.of("paid", false, "sandbox", true);
                }
                throw new IllegalStateException("支付宝沙箱查询失败，请检查配置后重试");
            }
            String status = response.getTradeStatus();
            if ("TRADE_CLOSED".equals(status))
            {
                jdbc.update("UPDATE sandbox_alipay_copyright_payment SET status='CLOSED' "
                        + "WHERE pay_order_id=? AND status='PENDING'", payOrderId);
                jdbc.update("UPDATE sys_copyright_pay_order SET status='closed', update_time=NOW() "
                        + "WHERE pay_order_id=? AND status IN ('unpaid','paying')", payOrderId);
                return Map.of("paid", false, "closed", true, "sandbox", true);
            }
            if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status))
            {
                return Map.of("paid", false, "sandbox", true);
            }
            if (!outTradeNo.equals(response.getOutTradeNo()) || response.getTradeNo() == null
                    || response.getTradeNo().isBlank() || response.getTotalAmount() == null
                    || amount.compareTo(new BigDecimal(response.getTotalAmount())) != 0)
            {
                throw new IllegalStateException("支付宝返回的支付单或金额不匹配");
            }

            jdbc.update("UPDATE sandbox_alipay_copyright_payment SET status='PAID',trade_no=? "
                    + "WHERE pay_order_id=? AND status IN ('PENDING','PAID')", response.getTradeNo(), payOrderId);
            jdbc.update("UPDATE sys_copyright_pay_order SET status='paid', paid_time=NOW(), update_time=NOW() "
                    + "WHERE pay_order_id=? AND status IN ('unpaid','paying','paid')", payOrderId);

            Map<String, Object> fulfilled = copyrightPayOrderService.fulfillPaidOrder(
                    payOrderId, buyerId, response.getTradeNo());
            Map<String, Object> result = new HashMap<>();
            result.put("paid", true);
            result.put("sandbox", true);
            result.put("applyId", fulfilled.get("applyId"));
            return result;
        }
        catch (AlipayApiException e)
        {
            throw new IllegalStateException("支付宝沙箱服务暂不可用");
        }
    }
}
