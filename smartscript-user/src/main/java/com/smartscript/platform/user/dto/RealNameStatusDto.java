package com.smartscript.platform.user.dto;

import java.util.Date;

/**
 * A5 实名状态（契约 §1.3）。
 *
 * 状态机：NOT_SUBMITTED -> PENDING -> APPROVED；PENDING -> REJECTED -> PENDING。
 * 姓名与证件号只返回掩码；材料引用不在此对象内（材料访问走 A4 的短时授权链路）。
 */
public class RealNameStatusDto
{
    private String status;
    private String realNameMasked;
    private String idNumberMasked;
    private String rejectReason;
    private Date submittedAt;
    private Date reviewedAt;

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRealNameMasked()
    {
        return realNameMasked;
    }

    public void setRealNameMasked(String realNameMasked)
    {
        this.realNameMasked = realNameMasked;
    }

    public String getIdNumberMasked()
    {
        return idNumberMasked;
    }

    public void setIdNumberMasked(String idNumberMasked)
    {
        this.idNumberMasked = idNumberMasked;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }

    public Date getSubmittedAt()
    {
        return submittedAt;
    }

    public void setSubmittedAt(Date submittedAt)
    {
        this.submittedAt = submittedAt;
    }

    public Date getReviewedAt()
    {
        return reviewedAt;
    }

    public void setReviewedAt(Date reviewedAt)
    {
        this.reviewedAt = reviewedAt;
    }
}
