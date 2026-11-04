package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysPromotionSlot;

/** 推广坑位配置 */
public interface SysPromotionSlotMapper
{
    List<SysPromotionSlot> selectSlots(@Param("slotType") String slotType);

    SysPromotionSlot selectSlotById(@Param("slotId") Long slotId);

    SysPromotionSlot selectSlotByNo(@Param("slotType") String slotType, @Param("slotNo") Integer slotNo);

    /** 行锁：审核/免费直上时串行化同一坑位的占用判定 */
    SysPromotionSlot selectSlotByNoForUpdate(@Param("slotType") String slotType, @Param("slotNo") Integer slotNo);

    int updatePrice(SysPromotionSlot slot);
}
