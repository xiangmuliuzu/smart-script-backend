package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysCopyrightSeal;
import com.smartscript.platform.content.domain.SysCopyrightSealLog;

/** 印章审核与状态持久化。 */
public interface SysCopyrightSealMapper
{
    List<SysCopyrightSeal> selectSealList(@Param("reviewStatus") String reviewStatus,
            @Param("sealStatus") String sealStatus, @Param("keyword") String keyword);

    SysCopyrightSeal selectSealById(@Param("sealId") Long sealId);

    List<SysCopyrightSealLog> selectSealLogs(@Param("sealId") Long sealId);

    int updateReview(@Param("sealId") Long sealId, @Param("reviewStatus") String reviewStatus,
            @Param("reason") String reason, @Param("reviewerId") Long reviewerId,
            @Param("updateBy") String updateBy);

    int updateSealStatus(@Param("sealId") Long sealId, @Param("expectedStatus") String expectedStatus,
            @Param("sealStatus") String sealStatus, @Param("updateBy") String updateBy);

    int resolveAbnormalSeal(@Param("sealId") Long sealId, @Param("reason") String reason,
            @Param("updateBy") String updateBy);

    int insertSealLog(SysCopyrightSealLog log);
}
