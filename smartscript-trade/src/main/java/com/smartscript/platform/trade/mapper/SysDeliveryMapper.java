package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysDelivery;

/**
 * 版权交割单 Mapper。
 */
public interface SysDeliveryMapper
{
    List<SysDelivery> selectDeliveryList(SysDelivery query);

    SysDelivery selectDeliveryById(Long deliveryId);

    SysDelivery selectDeliveryByOrderId(Long orderId);

    int insertDelivery(SysDelivery delivery);

    int updateDelivery(SysDelivery delivery);
}
