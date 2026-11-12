package com.smartscript.platform.trade.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.trade.domain.DemandSandboxPayment;

/**
 * C module: demand sandbox payment mapper (demand_sandbox_payment).
 * 记录需求任务的支付宝沙箱入账（escrow 托管 / deposit 押金）。
 */
public interface DemandSandboxPaymentMapper
{
    /** 幂等落一条 PENDING 记录；已存在则忽略（保留原 out_trade_no）。 */
    int upsertPending(@Param("demandId") Long demandId,
                      @Param("purpose") String purpose,
                      @Param("payerId") Long payerId,
                      @Param("amount") BigDecimal amount,
                      @Param("outTradeNo") String outTradeNo);

    /** 行锁读取当前记录（事务内使用）。 */
    DemandSandboxPayment selectForUpdate(@Param("demandId") Long demandId,
                                         @Param("purpose") String purpose);

    /** 按商户订单号读取（异步通知路由用）。 */
    DemandSandboxPayment selectByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    /** 某任务的收款记录（托管/押金各一行，含金额、付款人、状态与支付宝交易号）。 */
    List<DemandSandboxPayment> selectListByDemandId(@Param("demandId") Long demandId);

    /** 收款成功：置 PAID 并记录支付宝交易号。 */
    int markPaid(@Param("outTradeNo") String outTradeNo,
                 @Param("tradeNo") String tradeNo);

    /** 交易关闭：置 CLOSED。 */
    int markClosed(@Param("outTradeNo") String outTradeNo);

    /** 旧码失效后换新：仅待支付/已关闭记录可换商户单号并置回 PENDING。 */
    int rotatePending(@Param("demandId") Long demandId,
                      @Param("purpose") String purpose,
                      @Param("newOutTradeNo") String newOutTradeNo,
                      @Param("payerId") Long payerId,
                      @Param("amount") BigDecimal amount);
}
