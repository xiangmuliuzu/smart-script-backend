package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.domain.SysOfflineCooperation;
import com.smartscript.platform.trade.mapper.SysOfflineCooperationMapper;

/**
 * C module: cooperation record service（分工 15 线上合作意向 / 16 线下谈判）。
 * source=online -> 线上合作意向；source=offline -> 线下谈判。谈判时间复用 nextFollowAt。
 * PC 侧（商务跟进页）以只读列表/详情消费；create/update 供 APP 侧（APP-TRADE-04）复用同一 Service。
 */
@Service
public class TradeCooperationService
{
    public static final String SOURCE_ONLINE = "online";
    public static final String SOURCE_OFFLINE = "offline";
    public static final String STATUS_PENDING = "pending";

    @Autowired
    private SysOfflineCooperationMapper cooperationMapper;

    public List<SysOfflineCooperation> selectCooperationList(SysOfflineCooperation cooperation)
    {
        return cooperationMapper.selectCooperationList(cooperation);
    }

    public SysOfflineCooperation selectCooperationById(Long cooperationId)
    {
        return cooperationMapper.selectCooperationById(cooperationId);
    }

    public SysOfflineCooperation insertCooperation(SysOfflineCooperation cooperation)
    {
        if (cooperation.getWorkId() == null)
        {
            throw new ServiceException("作品 ID 不能为空");
        }
        if (cooperation.getCreatorId() == null)
        {
            throw new ServiceException("作者 ID 不能为空");
        }
        if (StringUtils.isEmpty(cooperation.getSource()))
        {
            cooperation.setSource(SOURCE_OFFLINE);
        }
        if (StringUtils.isEmpty(cooperation.getStatus()))
        {
            cooperation.setStatus(STATUS_PENDING);
        }
        Date now = new Date();
        cooperation.setCooperationNo("COOP" + Seq.getId());
        cooperation.setCreatedAt(now);
        cooperation.setUpdatedAt(now);
        cooperation.setCreateBy(SecurityUtils.getUsername());
        cooperation.setCreateTime(now);
        cooperationMapper.insertCooperation(cooperation);
        return cooperation;
    }

    public int updateCooperation(SysOfflineCooperation cooperation)
    {
        if (cooperation.getCooperationId() == null)
        {
            throw new ServiceException("合作记录 ID 不能为空");
        }
        Date now = new Date();
        cooperation.setUpdatedAt(now);
        cooperation.setUpdateBy(SecurityUtils.getUsername());
        cooperation.setUpdateTime(now);
        return cooperationMapper.updateCooperation(cooperation);
    }
}
