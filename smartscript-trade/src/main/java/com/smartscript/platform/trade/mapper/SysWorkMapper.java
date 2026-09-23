package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysWork;

public interface SysWorkMapper
{
    List<SysWork> selectTradeWorkList(SysWork work);
    SysWork selectWorkById(Long workId);
    int updateTradeSettings(SysWork work);
}
