package com.ruoyi.framework.realtime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 站内实时事件的下行出口。
 *
 * <p>约定：帧只携带「哪一类事情发生了」和定位用的 id，正文一律留在数据库里由客户端自行拉取。
 * 这样推送丢失或连接不在线都不会造成数据缺失，最坏情况退化成一次轮询。</p>
 *
 * <p>写连接是网络 IO，因此投递任务交给共享线程池，绝不占用业务事务线程；
 * 任何异常都在本类内吞掉——通知已经落库，推送失败不能反向影响主流程。</p>
 */
@Service
public class RealtimePushService
{
    private static final Logger log = LoggerFactory.getLogger(RealtimePushService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OnlineSessionRegistry registry;

    private final ThreadPoolTaskExecutor executor;

    public RealtimePushService(OnlineSessionRegistry registry,
            @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor executor)
    {
        this.registry = registry;
        this.executor = executor;
    }

    /** 站内通知创建成功后提醒收件人拉取收件箱。 */
    public void pushNotification(Long notificationId, String businessType, String title, Collection<Long> userIds)
    {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "notification");
        frame.put("notificationId", notificationId);
        frame.put("businessType", businessType);
        frame.put("title", title);
        push(frame, userIds);
    }

    /**
     * 通知落库所在事务提交后再推送。
     *
     * <p>必须在提交后：提交前推送，客户端收到帧立刻回查数据库，读到的还是旧快照，
     * 表现就是「提示响了但列表里什么都没有」。</p>
     */
    public void pushNotificationAfterCommit(Long notificationId, String businessType, String title,
            Collection<Long> userIds)
    {
        afterCommit(() -> pushNotification(notificationId, businessType, title, userIds));
    }

    /**
     * 聊天消息落库成功后提醒会话双方。
     *
     * 发送方也要收帧：同一账号的多台设备需要同步气泡，否则另一台设备仍要等下一次轮询。
     */
    public void pushChatMessage(Long sessionId, Long messageId, Long senderId, Long receiverId)
    {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "chat");
        frame.put("sessionId", sessionId);
        frame.put("messageId", messageId);
        frame.put("senderId", senderId);
        Set<Long> targets = new LinkedHashSet<>();
        if (receiverId != null)
        {
            targets.add(receiverId);
        }
        if (senderId != null)
        {
            targets.add(senderId);
        }
        push(frame, targets);
    }

    /** 聊天消息落库所在事务提交后再推送，理由同 {@link #pushNotificationAfterCommit}。 */
    public void pushChatMessageAfterCommit(Long sessionId, Long messageId, Long senderId, Long receiverId)
    {
        afterCommit(() -> pushChatMessage(sessionId, messageId, senderId, receiverId));
    }

    /**
     * 有事务上下文时挂到提交后回调，没有事务（如定时任务里已自行提交）则立即执行。
     * 回调内部异常不外抛——数据已经落库，推送只是加速可见。
     */
    private void afterCommit(Runnable task)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                try
                {
                    task.run();
                }
                catch (RuntimeException e)
                {
                    log.warn("Realtime after-commit push failed ({})", e.getClass().getSimpleName());
                }
            }
        });
    }

    /** 通用事件帧，供后续渠道（公告、任务状态）复用。 */
    public void push(Map<String, Object> frame, Collection<Long> userIds)
    {
        if (userIds == null || userIds.isEmpty())
        {
            return;
        }
        String payload;
        try
        {
            payload = MAPPER.writeValueAsString(frame);
        }
        catch (Exception e)
        {
            log.warn("Realtime frame serialization failed ({})", e.getClass().getSimpleName());
            return;
        }
        Set<Long> targets = new LinkedHashSet<>(userIds);
        try
        {
            executor.execute(() -> deliver(payload, targets));
        }
        catch (RuntimeException e)
        {
            // 线程池饱和时 CallerRunsPolicy 之外的拒绝策略：直接放弃推送，数据仍在库里
            log.debug("Realtime delivery rejected ({})", e.getClass().getSimpleName());
        }
    }

    private void deliver(String payload, Set<Long> targets)
    {
        int delivered = 0;
        for (Long userId : targets)
        {
            if (registry.send(userId, payload))
            {
                delivered++;
            }
        }
        if (delivered > 0)
        {
            log.debug("Realtime frame pushed to {} of {} online users", delivered, targets.size());
        }
    }
}
