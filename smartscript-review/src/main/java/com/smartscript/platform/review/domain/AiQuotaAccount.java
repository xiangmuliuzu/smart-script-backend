package com.smartscript.platform.review.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;

/**
 * AI配额账户实体
 *
 * @author smartscript
 */
public class AiQuotaAccount {

    private Long accountId;
    private Long userId;
    private BigDecimal availableQuota;
    private BigDecimal reservedQuota;
    private BigDecimal totalEarned;
    private BigDecimal totalConsumed;
    private BigDecimal totalRefunded;
    private String status;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;

    // 扩展字段
    private String nickname;
    private String phone;

    // getter and setter
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getAvailableQuota() { return availableQuota; }
    public void setAvailableQuota(BigDecimal availableQuota) { this.availableQuota = availableQuota; }
    public BigDecimal getReservedQuota() { return reservedQuota; }
    public void setReservedQuota(BigDecimal reservedQuota) { this.reservedQuota = reservedQuota; }
    public BigDecimal getTotalEarned() { return totalEarned; }
    public void setTotalEarned(BigDecimal totalEarned) { this.totalEarned = totalEarned; }
    public BigDecimal getTotalConsumed() { return totalConsumed; }
    public void setTotalConsumed(BigDecimal totalConsumed) { this.totalConsumed = totalConsumed; }
    public BigDecimal getTotalRefunded() { return totalRefunded; }
    public void setTotalRefunded(BigDecimal totalRefunded) { this.totalRefunded = totalRefunded; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
