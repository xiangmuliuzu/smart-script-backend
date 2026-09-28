package com.smartscript.platform.trade.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.smartscript.platform.trade.service.TradeInquiryService;

/**
 * C 模块交易定时任务（供若依 Quartz 通过 bean 名调用）。
 *
 * invoke_target: tradeInquiryTask.closeExpiredInquiries
 * 注册见迁移 C_20260928_005__c_inquiry_expire_job.sql（每 10 分钟执行一次）。
 *
 * 白名单说明：若依 quartz 白名单 Constants.JOB_WHITELIST_STR 仅含 com.ruoyi.quartz.task，
 * 且该白名单只在 SysJobController「新增/编辑」任务时校验；本任务经 SQL 迁移直接注册 sys_job，
 * 运行期（JobInvokeUtil.invokeMethod）不校验白名单，可正常调度，并可在 UI 暂停/恢复/立即执行。
 * 如需在 UI 编辑其调用串，需把本包加入白名单（涉及公共代码，须群里说明原因与影响范围）。
 *
 * 对应问题档案 P1-08：询盘过期无处理机制。
 */
@Component("tradeInquiryTask")
public class TradeInquiryTask
{
    private static final Logger log = LoggerFactory.getLogger(TradeInquiryTask.class);

    @Autowired
    private TradeInquiryService inquiryService;

    /**
     * 扫描并关闭过期询盘：status ∈ {pending,accepted,quoted} 且 expire_at < now → closed。
     */
    public void closeExpiredInquiries()
    {
        int count = inquiryService.closeExpiredInquiries();
        log.info("[C-Inquiry-Expire] 关闭过期询盘 {} 条", count);
    }
}
