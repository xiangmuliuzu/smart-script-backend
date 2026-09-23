package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysOrderStatusLog;

public interface SysOrderStatusLogMapper
{
    List<SysOrderStatusLog> selectLogsByOrderId(Long orderId);
    int insertLog(SysOrderStatusLog log);
}
