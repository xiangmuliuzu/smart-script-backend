package com.ruoyi.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 财务异常记录对象 sys_finance_abnormal
 */
public class SysFinanceAbnormal extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long abnormalId;

    @Excel(name = "异常类型", readConverterExp = "duplicate=重复支付,refund=退款异常,escrow=托管异常,settlement=结算异常")
    private String abnormalType;

    private String relatedNo;

    private Long orderId;

    private Long userId;

    @Excel(name = "异常金额")
    private BigDecimal abnormalAmount;

    private String abnormalReason;

    private String abnormalDesc;

    @Excel(name = "状态", readConverterExp = "pending=待处理,handled=已处理")
    private String status;

    private String handleSolution;

    private String handleRemark;

    private String handleBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date handleTime;

    private Integer duplicateCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getAbnormalId() {
        return abnormalId;
    }

    public void setAbnormalId(Long abnormalId) {
        this.abnormalId = abnormalId;
    }

    public String getAbnormalType() {
        return abnormalType;
    }

    public void setAbnormalType(String abnormalType) {
        this.abnormalType = abnormalType;
    }

    public String getRelatedNo() {
        return relatedNo;
    }

    public void setRelatedNo(String relatedNo) {
        this.relatedNo = relatedNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getAbnormalAmount() {
        return abnormalAmount;
    }

    public void setAbnormalAmount(BigDecimal abnormalAmount) {
        this.abnormalAmount = abnormalAmount;
    }

    public String getAbnormalReason() {
        return abnormalReason;
    }

    public void setAbnormalReason(String abnormalReason) {
        this.abnormalReason = abnormalReason;
    }

    public String getAbnormalDesc() {
        return abnormalDesc;
    }

    public void setAbnormalDesc(String abnormalDesc) {
        this.abnormalDesc = abnormalDesc;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHandleSolution() {
        return handleSolution;
    }

    public void setHandleSolution(String handleSolution) {
        this.handleSolution = handleSolution;
    }

    public String getHandleRemark() {
        return handleRemark;
    }

    public void setHandleRemark(String handleRemark) {
        this.handleRemark = handleRemark;
    }

    public String getHandleBy() {
        return handleBy;
    }

    public void setHandleBy(String handleBy) {
        this.handleBy = handleBy;
    }

    public Date getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(Date handleTime) {
        this.handleTime = handleTime;
    }

    public Integer getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(Integer duplicateCount) {
        this.duplicateCount = duplicateCount;
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
}
