package com.smartscript.platform.content.dto;

import java.util.Date;

/** 管理端免费直上推荐 */
public class PromotionGrantRequest
{
    private Long workId;
    private String slotType;
    private Integer slotNo;
    /** 开始时间（可选，默认立即） */
    private Date startTime;
    /** 投放天数（可选，默认 7） */
    private Integer days;
    /** 轮播素材图片地址（slotType=banner 可选，缺省回退作品封面） */
    private String materialUrl;
    /** 锁定标记（可选，默认 1）：1=独占锁定，0=可被付费素材覆盖的运营兜底 */
    private Integer exclusive;

    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getSlotType() { return slotType; }
    public void setSlotType(String slotType) { this.slotType = slotType; }
    public Integer getSlotNo() { return slotNo; }
    public void setSlotNo(Integer slotNo) { this.slotNo = slotNo; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public String getMaterialUrl() { return materialUrl; }
    public void setMaterialUrl(String materialUrl) { this.materialUrl = materialUrl; }
    public Integer getExclusive() { return exclusive; }
    public void setExclusive(Integer exclusive) { this.exclusive = exclusive; }
}
