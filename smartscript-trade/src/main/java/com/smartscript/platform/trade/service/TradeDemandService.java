package com.smartscript.platform.trade.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandSubmission;
import com.smartscript.platform.trade.mapper.SysDemandMapper;
import com.smartscript.platform.trade.mapper.SysDemandSubmissionMapper;

/**
 * C module: demand project query service.
 */
@Service
public class TradeDemandService
{
    @Autowired
    private SysDemandMapper demandMapper;

    @Autowired
    private SysDemandSubmissionMapper submissionMapper;

    public List<SysDemand> selectDemandList(SysDemand demand)
    {
        return demandMapper.selectDemandList(demand);
    }

    public SysDemand selectDemandById(Long demandId)
    {
        return demandMapper.selectDemandById(demandId);
    }

    public List<SysDemandSubmission> selectSubmissionsByDemandId(Long demandId)
    {
        return submissionMapper.selectSubmissionsByDemandId(demandId);
    }
}
