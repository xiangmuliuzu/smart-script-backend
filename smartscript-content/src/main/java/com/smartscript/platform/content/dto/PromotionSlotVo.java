package com.smartscript.platform.content.dto;

import java.math.BigDecimal;
import com.smartscript.platform.content.domain.SysPromotionApplication;

/**
 * 坑位视图（管理端 / App 端共用）
 *
 * state：
 *   idle 空闲 / locked 已锁坑（待支付）/ active 投放中 /
 *   reserved 已预约 / missing_material 待上传素材 / disabled 已停用
 *
 * @author xiangsipeng
 */
public class PromotionSlotVo
{
    private Long slotId;

    private String slotType;

    private Integer slotNo;

    private BigDecimal pricePerDay;

    /** 是否接受申请（1/0） */
    private String enabled;

    private String remark;

    /** 坑位状态 */
    private String state;

    /** 当前投放（active / locked / reserved；只含锁定单，App 据此判断不可预约） */
    private SysPromotionApplication current;

    /** 下一笔预约 */
    private SysPromotionApplication next;

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

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getState()
    {
        return state;
    }

    public void setState(String state)
    {
        this.state = state;
    }

    public SysPromotionApplication getCurrent()
    {
        return current;
    }

    public void setCurrent(SysPromotionApplication current)
    {
        this.current = current;
    }

    public SysPromotionApplication getNext()
    {
        return next;
    }

    public void setNext(SysPromotionApplication next)
    {
        this.next = next;
    }
}
