package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.trade.domain.SysPartner;
import com.smartscript.platform.trade.mapper.SysPartnerMapper;

/**
 * C module: partner management service.
 */
@Service
public class TradePartnerService
{
    @Autowired
    private SysPartnerMapper partnerMapper;

    public List<SysPartner> selectPartnerList(SysPartner partner)
    {
        return partnerMapper.selectPartnerList(partner);
    }

    public SysPartner selectPartnerById(Long partnerId)
    {
        return partnerMapper.selectPartnerById(partnerId);
    }

    public int insertPartner(SysPartner partner)
    {
        partner.setCreateBy(SecurityUtils.getUsername());
        partner.setCreateTime(new Date());
        if (partner.getCooperationCount() == null) { partner.setCooperationCount(0); }
        if (partner.getTotalAmount() == null) { partner.setTotalAmount(java.math.BigDecimal.ZERO); }
        if (partner.getStatus() == null) { partner.setStatus("active"); }
        return partnerMapper.insertPartner(partner);
    }
}
