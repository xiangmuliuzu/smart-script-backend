package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysDemandSubmission;

public interface SysDemandSubmissionMapper
{
    List<SysDemandSubmission> selectSubmissionsByDemandId(Long demandId);
}
