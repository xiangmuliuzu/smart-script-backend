package com.smartscript.platform.user.domain;

import java.util.Date;

/**
 * App 用户域：实名申请（规格 §8.4，映射 user_real_name_auth，A2 已建表）。
 *
 * 强制边界：本对象只承载掩码与材料引用，不持有身份证号明文；
 * 材料引用只在详情查询填充，列表不选择该列。
 */
public class UserRealNameAuth
{
    private Long applicationId;
    private Long userId;
    private String realNameMask;
    private String idNumberMask;
    private String materialRef;
    private String status;
    private String rejectReason;
    private Date submittedAt;
    private Date reviewedAt;

    public Long getApplicationId()
    {
        return applicationId;
    }

    public void setApplicationId(Long applicationId)
    {
        this.applicationId = applicationId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getRealNameMask()
    {
        return realNameMask;
    }

    public void setRealNameMask(String realNameMask)
    {
        this.realNameMask = realNameMask;
    }

    public String getIdNumberMask()
    {
        return idNumberMask;
    }

    public void setIdNumberMask(String idNumberMask)
    {
        this.idNumberMask = idNumberMask;
    }

    public String getMaterialRef()
    {
        return materialRef;
    }

    public void setMaterialRef(String materialRef) {
        this.materialRef = materialRef;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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
