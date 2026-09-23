package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysPartner;

public interface SysPartnerMapper
{
    List<SysPartner> selectPartnerList(SysPartner partner);
    SysPartner selectPartnerById(Long partnerId);
    int insertPartner(SysPartner partner);
    int updatePartner(SysPartner partner);
    int deletePartnerByIds(Long[] partnerIds);
}
