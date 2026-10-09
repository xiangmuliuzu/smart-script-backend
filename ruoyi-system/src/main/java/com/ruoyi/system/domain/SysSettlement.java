package com.ruoyi.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 结算单对象 sys_settlement
 */
public class SysSettlement extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long settlementId;

    @Excel(name = "结算单号")
    private String settlementNo;

    @Excel(name = "作者ID")
    private Long authorId;

    private Date periodStart;

    private Date periodEnd;

    @Excel(name = "订单数量")
    private Integer orderCount;

    @Excel(name = "总交易金额")
    private BigDecimal totalAmount;

    @Excel(name = "平台分成金额")
    private BigDecimal platformAmount;

    @Excel(name = "作者应得金额")
    private BigDecimal authorAmount;

    @Excel(name = "平台分成比例")
    private Integer platformRatio;

    @Excel(name = "结算状态", readConverterExp = "pending=待结算,settled=已结算,abnormal=异常")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date settlementTime;

    private String abnormalReason;

    private String handleRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getSettlementId() {
        return settlementId;
    }

    public void setSettlementId(Long settlementId) {
        this.settlementId = settlementId;
    }

    public String getSettlementNo() {
        return settlementNo;
    }

    public void setSettlementNo(String settlementNo) {
        this.settlementNo = settlementNo;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Date getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Date periodStart) {
        this.periodStart = periodStart;
    }

    public Date getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Date periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getPlatformAmount() {
        return platformAmount;
    }

    public void setPlatformAmount(BigDecimal platformAmount) {
        this.platformAmount = platformAmount;
    }

    public BigDecimal getAuthorAmount() {
        return authorAmount;
    }

    public void setAuthorAmount(BigDecimal authorAmount) {
        this.authorAmount = authorAmount;
    }

    public Integer getPlatformRatio() {
        return platformRatio;
    }

    public void setPlatformRatio(Integer platformRatio) {
        this.platformRatio = platformRatio;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getSettlementTime() {
        return settlementTime;
    }

    public void setSettlementTime(Date settlementTime) {
        this.settlementTime = settlementTime;
    }

    public String getAbnormalReason() {
        return abnormalReason;
    }

    public void setAbnormalReason(String abnormalReason) {
        this.abnormalReason = abnormalReason;
    }

    public String getHandleRemark() {
        return handleRemark;
    }

    public void setHandleRemark(String handleRemark) {
        this.handleRemark = handleRemark;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("settlementId", getSettlementId())
                .append("settlementNo", getSettlementNo())
                .append("authorId", getAuthorId())
                .append("periodStart", getPeriodStart())
                .append("periodEnd", getPeriodEnd())
                .append("orderCount", getOrderCount())
                .append("totalAmount", getTotalAmount())
                .append("platformAmount", getPlatformAmount())
                .append("authorAmount", getAuthorAmount())
                .append("platformRatio", getPlatformRatio())
                .append("status", getStatus())
                .append("settlementTime", getSettlementTime())
                .append("remark", getRemark())
                .toString();
    }
}
