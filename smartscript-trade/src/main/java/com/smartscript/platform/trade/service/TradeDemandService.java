package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.Seq;
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

    /**
     * 发布征集令（分工条目：COLLECT / POST /trade/demand）。
     *
     * 修复 COLLECT_001：前端仅传 {title, genreId, budget, deadline, contactInfo, requirement}，
     * 而 sys_demand.client_id 为 NOT NULL 且原 Service/Controller 都未赋值 → Column 'client_id' cannot be null。
     * 此处统一补齐默认值（发布人=当前登录用户、征集号、投稿数、状态 open）并做必填校验，
     * APP 侧复用同一入口。
     */
    public int insertDemand(SysDemand demand)
    {
        if (demand == null || demand.getTitle() == null || demand.getTitle().trim().isEmpty())
        {
            throw new RuntimeException("征集标题不能为空");
        }
        if (demand.getClientId() == null)
        {
            demand.setClientId(SecurityUtils.getUserId());
        }
        if (demand.getDemandNo() == null || demand.getDemandNo().isEmpty())
        {
            demand.setDemandNo("DM" + Seq.getId());
        }
        if (demand.getSubmissionCount() == null)
        {
            demand.setSubmissionCount(0);
        }
        if (demand.getStatus() == null || demand.getStatus().isEmpty())
        {
            demand.setStatus("open");
        }
        if (demand.getRequirement() == null)
        {
            demand.setRequirement("");
        }
        demand.setCreateBy(SecurityUtils.getUsername());
        demand.setCreateTime(new Date());
        return demandMapper.insertDemand(demand);
    }
}
