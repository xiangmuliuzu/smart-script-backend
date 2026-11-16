package com.smartscript.platform.content.dto;

import java.math.BigDecimal;
import java.util.List;

/** 位次单价 / 启停批量配置 */
public class PromotionPriceRequest
{
    private List<Item> slots;

    public List<Item> getSlots() { return slots; }
    public void setSlots(List<Item> slots) { this.slots = slots; }

    public static class Item
    {
        private Long slotId;
        private BigDecimal pricePerDay;
        private String enabled;

        public Long getSlotId() { return slotId; }
        public void setSlotId(Long slotId) { this.slotId = slotId; }
        public BigDecimal getPricePerDay() { return pricePerDay; }
        public void setPricePerDay(BigDecimal pricePerDay) { this.pricePerDay = pricePerDay; }
        public String getEnabled() { return enabled; }
        public void setEnabled(String enabled) { this.enabled = enabled; }
    }
}
