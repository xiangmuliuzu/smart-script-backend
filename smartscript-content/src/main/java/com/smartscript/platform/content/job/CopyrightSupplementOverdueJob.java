package com.smartscript.platform.content.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.smartscript.platform.content.service.ICopyrightThirdPartyMessageService;

/**
 * 每小时扫描超时未补正申请 → supplement_overdue（逾期待驳回）。
 */
@Component
public class CopyrightSupplementOverdueJob
{
    private static final Logger log = LoggerFactory.getLogger(CopyrightSupplementOverdueJob.class);

    private final ICopyrightThirdPartyMessageService messageService;

    public CopyrightSupplementOverdueJob(ICopyrightThirdPartyMessageService messageService)
    {
        this.messageService = messageService;
    }

    @Scheduled(cron = "0 15 * * * ?")
    public void closeOverdue()
    {
        try
        {
            int n = messageService.closeOverdueSupplements();
            if (n > 0)
            {
                log.info("版权补正逾期处理完成 count={}", n);
            }
        }
        catch (Exception e)
        {
            log.error("版权补正逾期扫描失败", e);
        }
    }
}
