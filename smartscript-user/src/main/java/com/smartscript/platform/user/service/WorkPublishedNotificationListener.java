package com.smartscript.platform.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import com.smartscript.platform.review.event.WorkPublishedEvent;

/**
 * 审核域「作品发布」事件的用户侧监听器：给作者粉丝投递关注动态通知。
 *
 * <p>只监听提交后事件：审核事务回滚时不发送。投递使用 REQUIRES_NEW 独立事务，
 * 且吞掉所有异常——通知只是审核结果的附带动作，失败绝不反向影响审核。</p>
 */
@Component
public class WorkPublishedNotificationListener
{
    private static final Logger log = LoggerFactory.getLogger(WorkPublishedNotificationListener.class);

    private final WorkPublishedNotifyService notifyService;
    private final TransactionTemplate newTx;

    public WorkPublishedNotificationListener(WorkPublishedNotifyService notifyService,
            PlatformTransactionManager transactionManager)
    {
        this.notifyService = notifyService;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkPublished(WorkPublishedEvent event)
    {
        if (event == null)
        {
            return;
        }
        try
        {
            newTx.executeWithoutResult(status -> notifyService.notifyFollowers(
                    event.workId(), event.authorId(), event.workTitle(), event.authorName()));
        }
        catch (Exception e)
        {
            // 审核已生效，通知失败仅告警
            log.warn("关注动态通知投递失败 workId={}: {}", event.workId(), e.getMessage());
        }
    }
}
