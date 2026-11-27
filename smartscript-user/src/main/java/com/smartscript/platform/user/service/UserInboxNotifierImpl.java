package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.smartscript.platform.notify.UserInboxNotifier;

/**
 * 业务模块站内信投递实现：独立事务 + 吞异常，失败不影响主业务。
 */
@Service
public class UserInboxNotifierImpl implements UserInboxNotifier
{
    private static final Logger log = LoggerFactory.getLogger(UserInboxNotifierImpl.class);

    private final UserNotificationAdminService notificationService;
    private final TransactionTemplate newTx;

    public UserInboxNotifierImpl(UserNotificationAdminService notificationService,
            PlatformTransactionManager transactionManager)
    {
        this.notificationService = notificationService;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void notify(String requestId, String type, String title, String content,
            String businessType, String businessId, List<Long> userIds)
    {
        List<Long> receivers = normalize(userIds);
        if (receivers.isEmpty() || requestId == null || requestId.isBlank()
                || type == null || type.isBlank()
                || title == null || title.isBlank()
                || content == null || content.isBlank())
        {
            return;
        }
        try
        {
            newTx.executeWithoutResult(status -> notificationService.create(
                    requestId, type, title, content, businessType, businessId, receivers, "system"));
        }
        catch (Exception e)
        {
            log.warn("站内信投递失败 requestId={}: {}", requestId, e.getMessage());
        }
    }

    private static List<Long> normalize(List<Long> userIds)
    {
        if (userIds == null || userIds.isEmpty())
        {
            return List.of();
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : userIds)
        {
            if (id != null && id > 0)
            {
                set.add(id);
            }
        }
        return new ArrayList<>(set);
    }
}
