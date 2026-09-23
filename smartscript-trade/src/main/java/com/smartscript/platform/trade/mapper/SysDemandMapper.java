package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysDemand;

public interface SysDemandMapper
{
    List<SysDemand> selectDemandList(SysDemand demand);
    SysDemand selectDemandById(Long demandId);
}
