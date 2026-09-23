package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysOrder;

public interface SysOrderMapper
{
    List<SysOrder> selectOrderList(SysOrder order);
    SysOrder selectOrderById(Long orderId);
    SysOrder selectOrderByInquiryId(Long inquiryId);
    int insertOrder(SysOrder order);
    int updateOrder(SysOrder order);
}
