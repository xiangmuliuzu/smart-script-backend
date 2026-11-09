package com.smartscript.platform.review.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/**
 * 积分充值订单实体（sys_recharge_order）。
 *
 * 当前为「模拟支付壳子」：创建订单 → 模拟支付成功 → 积分入账。
 * 真实微信/支付宝下单与回调由支付组在此表上补齐（status/pay_time 字段已预留）。
 *
 * @author smartscript
 */
public class RechargeOrder {

    private Long orderId;
    /** 平台订单号（唯一，支付回调按此对账） */
    private String orderNo;
    private Long userId;
    /** 支付金额（分） */
    private Integer amountFen;
    /** 到账积分 */
    private Integer points;
    /** 支付渠道 WECHAT-微信 ALIPAY-支付宝 */
    private String channel;
    /** 订单状态 PENDING-待支付 PAID-已支付 CANCELLED-已取消 */
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date payTime;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getAmountFen() { return amountFen; }
    public void setAmountFen(Integer amountFen) { this.amountFen = amountFen; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
