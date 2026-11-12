package com.smartscript.platform.content.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.smartscript.platform.content.service.IPromotionService;

/**
 * 推广位覆盖清理：不锁定的运营素材被生效中的锁定/付费投放覆盖时自动下架。
 *
 * 每 5 分钟扫描一次，保证作者付费投放开始生效后，原运营素材不会继续展示；
 * 管理员打开位次页时也会同步触发一次，PC 端看到的结果始终是最新的。
 */
@Component
public class PromotionCoverOverlapJob
{
    private static final Logger log = LoggerFactory.getLogger(PromotionCoverOverlapJob.class);

    private final IPromotionService promotionService;

    public PromotionCoverOverlapJob(IPromotionService promotionService)
    {
        this.promotionService = promotionService;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void cancelCovered()
    {
        try
        {
            int n = promotionService.cancelCoveredFree();
            if (n > 0)
            {
                log.info("推广位覆盖清理完成 count={}", n);
            }
        }
        catch (Exception e)
        {
            log.error("推广位覆盖清理失败", e);
        }
    }
}
