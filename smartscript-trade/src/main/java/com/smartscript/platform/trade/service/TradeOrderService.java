package com.smartscript.platform.trade.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.domain.SysOrderStatusLog;
import com.smartscript.platform.trade.mapper.SysOrderMapper;
import com.smartscript.platform.trade.mapper.SysOrderStatusLogMapper;

/**
 * C module: order query service.
 * C owns order creation + status flow; contract/escrow/settlement belongs to D.
 */
@Service
public class TradeOrderService
{
    @Autowired
    private SysOrderMapper orderMapper;

    @Autowired
    private SysOrderStatusLogMapper logMapper;

    public List<SysOrder> selectOrderList(SysOrder order)
    {
        return orderMapper.selectOrderList(order);
    }

    public SysOrder selectOrderById(Long orderId)
    {
        return orderMapper.selectOrderById(orderId);
    }

    public List<SysOrderStatusLog> selectStatusLogByOrderId(Long orderId)
    {
        return logMapper.selectLogsByOrderId(orderId);
    }
}
