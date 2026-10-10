package com.smartscript.platform.review.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 积分账户实体（sys_points_account）
 *
 * @author smartscript
 */
public class PointsAccount {

    private Long accountId;
    private Long userId;
    /** 积分余额 */
    private Integer balance;
    /** 累计获得 */
    private Integer totalEarned;
    /** 累计消耗 */
    private Integer totalSpent;
    /** 今日获得 */
    private Integer todayEarned;
    private Date lastUpdateDate;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private String remark;
    /** 关联用户昵称（联表查询） */
    private String nickname;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getBalance() { return balance; }
    public void setBalance(Integer balance) { this.balance = balance; }
    public Integer getTotalEarned() { return totalEarned; }
    public void setTotalEarned(Integer totalEarned) { this.totalEarned = totalEarned; }
    public Integer getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Integer totalSpent) { this.totalSpent = totalSpent; }
    public Integer getTodayEarned() { return todayEarned; }
    public void setTodayEarned(Integer todayEarned) { this.todayEarned = todayEarned; }
    public Date getLastUpdateDate() { return lastUpdateDate; }
    public void setLastUpdateDate(Date lastUpdateDate) { this.lastUpdateDate = lastUpdateDate; }
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
}
