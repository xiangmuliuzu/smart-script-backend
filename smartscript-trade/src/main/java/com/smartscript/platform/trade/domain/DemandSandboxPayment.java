package com.smartscript.platform.trade.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * C module: demand sandbox payment record (demand_sandbox_payment).
 * 需求任务的支付宝沙箱入账记录，仅记录真实收款的两个用途：
 * escrow（发布者托管付款）与 deposit（创作者押金付款）。
 */
public class DemandSandboxPayment
{
    public static final String PURPOSE_ESCROW  = "escrow";
    public static final String PURPOSE_DEPOSIT = "deposit";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID    = "PAID";
    public static final String STATUS_CLOSED  = "CLOSED";

    private Long id;
    private Long demandId;
    private String purpose;
    private Long payerId;
    private BigDecimal amount;
    private String outTradeNo;
    private String tradeNo;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDemandId() { return demandId; }
    public void setDemandId(Long demandId) { this.demandId = demandId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Long getPayerId() { return payerId; }
    public void setPayerId(Long payerId) { this.payerId = payerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String outTradeNo) { this.outTradeNo = outTradeNo; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
