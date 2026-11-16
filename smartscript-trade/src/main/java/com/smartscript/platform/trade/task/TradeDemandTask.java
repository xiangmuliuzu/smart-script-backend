package com.smartscript.platform.trade.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.smartscript.platform.trade.service.TradeDemandService;

/**
 * C 模块征集任务定时任务（供若依 Quartz 通过 bean 名调用）。
 *
 * invoke_target: tradeDemandTask.expireOverdueRounds
 * 注册见迁移 C_20261103_003__demand_timeout_job.sql（每 10 秒执行一次）。
 *
 * 白名单说明：若依 quartz 白名单 Constants.JOB_WHITELIST_STR 仅含 com.ruoyi.quartz.task，
 * 且该白名单只在 SysJobController「新增/编辑」任务时校验；本任务经 SQL 迁移直接注册 sys_job，
 * 运行期（JobInvokeUtil.invokeMethod）不校验白名单，可正常调度，并可在 UI 暂停/恢复/立即执行。
 * 如需在 UI 编辑其调用串，需把本包加入白名单（涉及公共代码，须群里说明原因与影响范围）。
 *
 * 作用：把「活动轮到点即失败」从读时惰性判定升级为近实时处置，
 * 同时把发布者超过 24 小时未处理的延时申请自动驳回。
 */
@Component("tradeDemandTask")
public class TradeDemandTask
{
    private static final Logger log = LoggerFactory.getLogger(TradeDemandTask.class);

    @Autowired
    private TradeDemandService demandService;

    /**
     * ① 自动驳回发布者超 24 小时未处理的延时申请；② 终止已过截止且仍在计时的活动轮。
     * 仅在确有处置时打日志，避免每 10 秒刷一条空扫描记录。
     */
    public void expireOverdueRounds()
    {
        int count = demandService.expireOverdueRounds();
        if (count > 0)
        {
            log.info("[C-Demand-Expire] 超时处置生效 {} 条（延时自动驳回 + 任务超时终止）", count);
        }
    }
}
