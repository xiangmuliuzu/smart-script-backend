package com.smartscript.platform.content.domain;

import java.math.BigDecimal;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 作品推广坑位（sys_promotion_slot）
 *
 * 依据：B_20261104_001__promotion_system.sql。
 * slot_type：banner 首页轮播 / search 搜索页 / hot 首页热门精选。
 *
 * @author xiangsipeng
 */
public class SysPromotionSlot extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 坑位ID */
    private Long slotId;

    /** 资源位类型 */
    private String slotType;

    /** 位次 */
    private Integer slotNo;

    /** 单价（元/天） */
    private BigDecimal pricePerDay;

    /** 是否接受申请（1 是 0 否） */
    private String enabled;

    public Long getSlotId()
    {
        return slotId;
    }

    public void setSlotId(Long slotId)
    {
        this.slotId = slotId;
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

    public BigDecimal getPricePerDay()
    {
        return pricePerDay;
    }

    public void setPricePerDay(BigDecimal pricePerDay)
    {
        this.pricePerDay = pricePerDay;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }
}
