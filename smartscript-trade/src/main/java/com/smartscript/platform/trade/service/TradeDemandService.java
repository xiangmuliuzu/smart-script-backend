package com.smartscript.platform.trade.service;

import java.util.Calendar;
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
        // 修复 COLLECT_002：截止日期不得早于当前日期（按天比较，当天允许）
        if (demand.getDeadline() != null && startOfDay(demand.getDeadline()).before(startOfDay(new Date())))
        {
            throw new RuntimeException("截止日期不能早于当前日期");
        }
        // 修复 COLLECT_003：纯电话录入（仅数字/横线/空格）必须符合标准格式，
        // 混合文本（如「姓名 + 电话」、邮箱）不做格式限制，避免误伤
        String contact = demand.getContactInfo() == null ? "" : demand.getContactInfo().trim();
        if (!contact.isEmpty() && contact.matches("^[\\d\\s-]+$")
                && !contact.matches("^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8}|(400|800)-?\\d{3}-?\\d{4})$"))
        {
            throw new RuntimeException("联系电话格式不正确，请输入 11 位手机号或带区号的固定电话");
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

    private static Date startOfDay(Date d)
    {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
