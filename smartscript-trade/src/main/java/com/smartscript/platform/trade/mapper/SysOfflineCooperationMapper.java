package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysOfflineCooperation;

public interface SysOfflineCooperationMapper
{
    List<SysOfflineCooperation> selectCooperationList(SysOfflineCooperation cooperation);
    SysOfflineCooperation selectCooperationById(Long cooperationId);
    int insertCooperation(SysOfflineCooperation cooperation);
    int updateCooperation(SysOfflineCooperation cooperation);
}
