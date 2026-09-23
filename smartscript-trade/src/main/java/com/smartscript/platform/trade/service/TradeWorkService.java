package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.trade.domain.SysWork;
import com.smartscript.platform.trade.mapper.SysWorkMapper;

/**
 * C module: trade work management service.
 * Handles listing trade works and updating trade settings (price, license type, etc.).
 */
@Service
public class TradeWorkService
{
    @Autowired
    private SysWorkMapper workMapper;

    public List<SysWork> selectTradeWorkList(SysWork work)
    {
        return workMapper.selectTradeWorkList(work);
    }

    public SysWork selectWorkById(Long workId)
    {
        return workMapper.selectWorkById(workId);
    }

    /**
     * Update trade settings for a work. Requires the work to be already reviewed/approved.
     */
    public int updateTradeSettings(SysWork work)
    {
        work.setUpdateBy(SecurityUtils.getUsername());
        work.setUpdateTime(new Date());
        return workMapper.updateTradeSettings(work);
    }
}
