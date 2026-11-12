package com.smartscript.platform.content.domain;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 作品推广申请 / 投放记录（sys_promotion_application）
 *
 * 状态机：
 *   pending 待审核 → rejected 已驳回
 *                  → approved 待支付（锁坑 48h）→ paid 已支付（按时间窗派生待生效/投放中/已结束）
 *   admin_grant 免费直上直接落 paid。
 *
 * @author xiangsipeng
 */
public class SysPromotionApplication extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 申请ID */
    private Long applyId;

    /** 申请单号 */
    private String applyNo;

    /** 作品ID */
    private Long workId;

    /** 作者ID */
    private Long authorId;

    /** 资源位类型 */
    private String slotType;

    /** 位次 */
    private Integer slotNo;

    /** 投放开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 投放结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    /** 投放天数 */
    private Integer days;

    /** 金额 */
    private BigDecimal amount;

    /** 来源：author_paid/admin_grant */
    private String source;

    /** 锁定标记：1=锁定独占（付费投放/管理员锁定），0=可被付费素材覆盖的运营兜底 */
    private Integer exclusive;

    /** 状态：pending/approved/rejected/paid/cancelled */
    private String status;

    /** 支付状态：unpaid/paid/closed */
    private String payStatus;

    /** 支付时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date payTime;

    /** 轮播素材ID */
    private Long bannerId;

    /** 轮播素材图片地址（作者申请时提交） */
    private String materialUrl;

    /** 驳回原因 */
    private String rejectReason;

    /** 锁坑截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lockExpireAt;

    /** 审核人 */
    private String auditBy;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date auditTime;

    // ---------- 列表 JOIN 扩展字段 ----------

    /** 作品标题 */
    private String workTitle;

    /** 作品封面 */
    private String workCover;

    /** 作者昵称 */
    private String authorName;

    /** 派生状态文案（待审核/待支付/待生效/投放中/已结束/已驳回/已取消） */
    private String statusLabel;

    /** 锁坑剩余秒数（仅 approved 且未过期时有值） */
    private Long lockRemainSeconds;

    /** 查询条件：作品/作者关键词 */
    private String keyword;

    /** 查询条件：申请单号 */
    private String applyNoQuery;

    /** 查询条件：派生状态（pending/locked/waiting/active/ended/rejected） */
    private String viewStatus;

    public Long getApplyId()
    {
        return applyId;
    }

    public void setApplyId(Long applyId)
    {
        this.applyId = applyId;
    }

    public String getApplyNo()
    {
        return applyNo;
    }

    public void setApplyNo(String applyNo)
    {
        this.applyNo = applyNo;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public Long getAuthorId()
    {
        return authorId;
    }

    public void setAuthorId(Long authorId)
    {
        this.authorId = authorId;
    }

    public String getSlotType()
    {
        return slotType;
    }

    public void setSlotType(String slotType)
    {
        this.slotType = slotType;
    }

    public Integer getSlotNo()
    {
        return slotNo;
    }

    public void setSlotNo(Integer slotNo)
    {
        this.slotNo = slotNo;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getEndTime()
    {
        return endTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }

    public Integer getDays()
    {
        return days;
    }

    public void setDays(Integer days)
    {
        this.days = days;
    }

    public BigDecimal getAmount()
    {
        return amount;
    }

    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public Integer getExclusive()
    {
        return exclusive;
    }

    public void setExclusive(Integer exclusive)
    {
        this.exclusive = exclusive;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getPayStatus()
    {
        return payStatus;
    }

    public void setPayStatus(String payStatus)
    {
        this.payStatus = payStatus;
    }

    public Date getPayTime()
    {
        return payTime;
    }

    public void setPayTime(Date payTime)
    {
        this.payTime = payTime;
    }

    public Long getBannerId()
    {
        return bannerId;
    }

    public void setBannerId(Long bannerId)
    {
        this.bannerId = bannerId;
    }

    public String getMaterialUrl()
    {
        return materialUrl;
    }

    public void setMaterialUrl(String materialUrl)
    {
        this.materialUrl = materialUrl;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }

    public Date getLockExpireAt()
    {
        return lockExpireAt;
    }

    public void setLockExpireAt(Date lockExpireAt)
    {
        this.lockExpireAt = lockExpireAt;
    }

    public String getAuditBy()
    {
        return auditBy;
    }

    public void setAuditBy(String auditBy)
    {
        this.auditBy = auditBy;
    }

    public Date getAuditTime()
    {
        return auditTime;
    }

    public void setAuditTime(Date auditTime)
    {
        this.auditTime = auditTime;
    }

    public String getWorkTitle()
    {
        return workTitle;
    }

    public void setWorkTitle(String workTitle)
    {
        this.workTitle = workTitle;
    }

    public String getWorkCover()
    {
        return workCover;
    }

    public void setWorkCover(String workCover)
    {
        this.workCover = workCover;
    }

    public String getAuthorName()
    {
        return authorName;
    }

    public void setAuthorName(String authorName)
    {
        this.authorName = authorName;
    }

    public String getStatusLabel()
    {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel)
    {
        this.statusLabel = statusLabel;
    }

    public Long getLockRemainSeconds()
    {
        return lockRemainSeconds;
    }

    public void setLockRemainSeconds(Long lockRemainSeconds)
    {
        this.lockRemainSeconds = lockRemainSeconds;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public String getApplyNoQuery()
    {
        return applyNoQuery;
    }

    public void setApplyNoQuery(String applyNoQuery)
    {
        this.applyNoQuery = applyNoQuery;
    }

    public String getViewStatus()
    {
        return viewStatus;
    }

    public void setViewStatus(String viewStatus)
    {
        this.viewStatus = viewStatus;
    }
}
