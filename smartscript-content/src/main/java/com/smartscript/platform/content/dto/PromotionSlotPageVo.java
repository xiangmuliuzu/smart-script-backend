package com.smartscript.platform.content.dto;

import java.util.List;
import com.smartscript.platform.content.domain.SysPromotionApplication;

/** 坑位总览页数据 */
public class PromotionSlotPageVo
{
    private PromotionOverviewVo overview;
    private List<PromotionSlotVo> slots;
    private List<SysPromotionApplication> upcoming;

    public PromotionOverviewVo getOverview() { return overview; }
    public void setOverview(PromotionOverviewVo overview) { this.overview = overview; }
    public List<PromotionSlotVo> getSlots() { return slots; }
    public void setSlots(List<PromotionSlotVo> slots) { this.slots = slots; }
    public List<SysPromotionApplication> getUpcoming() { return upcoming; }
    public void setUpcoming(List<SysPromotionApplication> upcoming) { this.upcoming = upcoming; }
}
