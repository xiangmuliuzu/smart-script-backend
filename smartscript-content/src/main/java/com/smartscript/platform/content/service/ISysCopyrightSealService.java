package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysCopyrightSeal;
import com.smartscript.platform.content.domain.SysCopyrightSealLog;

/** PC 印章审核与状态管理。 */
public interface ISysCopyrightSealService
{
    List<SysCopyrightSeal> selectSealList(String reviewStatus, String sealStatus, String keyword);

    SysCopyrightSeal selectSealById(Long sealId);

    List<SysCopyrightSealLog> selectSealLogs(Long sealId);

    boolean reviewSeal(Long sealId, String action, String reason, Long operatorId, String operatorName);

    boolean updateSealStatus(Long sealId, String targetStatus, Long operatorId, String operatorName);

    boolean resolveAbnormalSeal(Long sealId, String reason, Long operatorId, String operatorName);
}
