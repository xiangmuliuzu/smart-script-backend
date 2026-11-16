package com.smartscript.platform.trade.service;

import java.math.BigDecimal;
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

/**
 * 支付宝沙箱 · 作品推广单支付
 *
 * 与订单支付（AlipaySandboxPaymentService）同源：同一套沙箱凭证、同一套「创建预下单 +
 * 主动查单确认」流程；支付记录落 sandbox_alipay_promotion_payment（按 apply_id 唯一）。
 * 确认成功后把 sys_promotion_application 置为 paid，投放按时间窗自动生效。
 *
 * @author xiangsipeng
 */
@Service
public class AlipaySandboxPromotionPaymentService
{
    private final JdbcTemplate jdbc;

    private final AlipaySandboxPaymentService sandbox;

    public AlipaySandboxPromotionPaymentService(JdbcTemplate jdbc, AlipaySandboxPaymentService sandbox)
    {
        this.jdbc = jdbc;
        this.sandbox = sandbox;
    }

    /** 校验推广单归属与可支付状态 */
    private Map<String, Object> buyerApply(Long applyId, Long buyerId)
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT apply_id,apply_no,author_id,amount,status,pay_status,lock_expire_at "
                        + "FROM sys_promotion_application WHERE apply_id=?", applyId);
        if (rows.isEmpty())
        {
            throw new IllegalArgumentException("推广申请不存在");
        }
        Map<String, Object> row = rows.get(0);
        if (buyerId == null || ((Number) row.get("author_id")).longValue() != buyerId)
        {
            throw new IllegalArgumentException("推广申请不存在或无权付款");
        }
        if (!"approved".equals(row.get("status")) || !"unpaid".equals(row.get("pay_status")))
        {
            throw new IllegalStateException("当前推广申请状态不可付款");
        }
        Object lock = row.get("lock_expire_at");
        if (lock instanceof java.sql.Timestamp && ((java.sql.Timestamp) lock).before(new java.util.Date()))
        {
            throw new IllegalStateException("锁坑已失效，请重新提交推广申请");
        }
        BigDecimal amount = (BigDecimal) row.get("amount");
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2)
        {
            throw new IllegalStateException("推广金额无效");
        }
        return row;
    }

    @Transactional
    public Map<String, Object> create(Long applyId, Long buyerId)
    {
        sandbox.requireSandbox();
        Map<String, Object> apply = buyerApply(applyId, buyerId);
        BigDecimal amount = (BigDecimal) apply.get("amount");

        String candidate = "SSPR" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sandbox_alipay_promotion_payment(apply_id,out_trade_no,buyer_id,amount) "
                + "VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE apply_id=apply_id", applyId, candidate, buyerId, amount);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT out_trade_no,buyer_id,amount,status FROM sandbox_alipay_promotion_payment WHERE apply_id=? FOR UPDATE",
                applyId);
        if (((Number) row.get("buyer_id")).longValue() != buyerId
                || amount.compareTo((BigDecimal) row.get("amount")) != 0)
        {
            throw new IllegalStateException("支付记录与推广单不一致");
        }
        String outTradeNo = (String) row.get("out_trade_no");
        if ("CLOSED".equals(row.get("status")))
        {
            jdbc.update("UPDATE sandbox_alipay_promotion_payment SET out_trade_no=?,status='PENDING' "
                    + "WHERE apply_id=? AND status='CLOSED'", candidate, applyId);
            outTradeNo = candidate;
        }
        else if (!"PENDING".equals(row.get("status")))
        {
            throw new IllegalStateException("推广单已支付");
        }

        try
        {
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(outTradeNo);
            model.setTotalAmount(amount.toPlainString());
            model.setSubject("剧云策作品推广 " + apply.get("apply_no"));
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setBizModel(model);
            AlipayTradePrecreateResponse response = sandbox.client().execute(request);
            if (!response.isSuccess() || !outTradeNo.equals(response.getOutTradeNo())
                    || response.getQrCode() == null || response.getQrCode().isBlank())
            {
                throw new IllegalStateException("支付宝沙箱创建支付失败，请检查沙箱配置");
            }
            return Map.of("qrCode", response.getQrCode(), "sandbox", true);
        }
        catch (AlipayApiException e)
        {
            throw new IllegalStateException("支付宝沙箱服务暂不可用");
        }
    }

    @Transactional
    public Map<String, Object> confirm(Long applyId, Long buyerId)
    {
        sandbox.requireSandbox();
        Map<String, Object> apply = buyerApply(applyId, buyerId);
        BigDecimal amount = (BigDecimal) apply.get("amount");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT out_trade_no,buyer_id,amount,status FROM sandbox_alipay_promotion_payment WHERE apply_id=? FOR UPDATE",
                applyId);
        if (rows.isEmpty())
        {
            throw new IllegalStateException("请先发起沙箱支付");
        }
        Map<String, Object> row = rows.get(0);
        if (((Number) row.get("buyer_id")).longValue() != buyerId
                || amount.compareTo((BigDecimal) row.get("amount")) != 0)
        {
            throw new IllegalStateException("支付记录与推广单不一致");
        }
        if ("PAID".equals(row.get("status")))
        {
            return Map.of("paid", true, "sandbox", true);
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
                jdbc.update("UPDATE sandbox_alipay_promotion_payment SET status='CLOSED' "
                        + "WHERE apply_id=? AND status='PENDING'", applyId);
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
                throw new IllegalStateException("支付宝返回的推广单或金额不匹配");
            }
            jdbc.update("UPDATE sys_promotion_application SET status='paid',pay_status='paid',"
                    + "pay_time=sysdate(),lock_expire_at=null,update_time=sysdate() "
                    + "WHERE apply_id=? AND status='approved' AND pay_status='unpaid'", applyId);
            jdbc.update("UPDATE sandbox_alipay_promotion_payment SET status='PAID',trade_no=? "
                    + "WHERE apply_id=? AND status='PENDING'", response.getTradeNo(), applyId);
            return Map.of("paid", true, "sandbox", true);
        }
        catch (AlipayApiException e)
        {
            throw new IllegalStateException("支付宝沙箱服务暂不可用");
        }
    }
}
