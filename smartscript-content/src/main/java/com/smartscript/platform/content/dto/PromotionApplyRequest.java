package com.smartscript.platform.content.dto;

import java.util.Date;

/** App 提交推广申请 */
public class PromotionApplyRequest
{
    private Long workId;
    private String slotType;
    private Integer slotNo;
    /** 投放天数（7/15/30） */
    private Integer days;
    /** 期望开始时间（可选，默认立即） */
    private Date startTime;
    /** 轮播素材图片地址（slotType=banner 时必填） */
    private String materialUrl;

    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getSlotType() { return slotType; }
    public void setSlotType(String slotType) { this.slotType = slotType; }
    public Integer getSlotNo() { return slotNo; }
    public void setSlotNo(Integer slotNo) { this.slotNo = slotNo; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public String getMaterialUrl() { return materialUrl; }
    public void setMaterialUrl(String materialUrl) { this.materialUrl = materialUrl; }
}
