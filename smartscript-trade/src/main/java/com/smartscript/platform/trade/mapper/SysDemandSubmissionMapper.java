package com.smartscript.platform.trade.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.trade.domain.SysDemandSubmission;

public interface SysDemandSubmissionMapper
{
    List<SysDemandSubmission> selectSubmissionsByDemandId(Long demandId);
    SysDemandSubmission selectSubmissionById(Long submissionId);
    int insertSubmission(SysDemandSubmission submission);
    int updateStatus(@Param("submissionId") Long submissionId, @Param("status") String status,
                     @Param("reviewRemark") String reviewRemark, @Param("updateBy") String updateBy);
    int batchRejectByDemandId(@Param("demandId") Long demandId, @Param("excludeId") Long excludeId,
                              @Param("updateBy") String updateBy);
    List<SysDemandSubmission> selectBySubmitterId(@Param("submitterId") Long submitterId);
}
