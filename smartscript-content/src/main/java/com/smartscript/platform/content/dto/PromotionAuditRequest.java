package com.smartscript.platform.content.dto;

/** 管理端审核请求 */
public class PromotionAuditRequest
{
    private Boolean approved;
    private String rejectReason;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
