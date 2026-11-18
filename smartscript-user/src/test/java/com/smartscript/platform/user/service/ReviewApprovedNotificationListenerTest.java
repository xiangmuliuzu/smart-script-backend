package com.smartscript.platform.user.service;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.smartscript.platform.review.service.ReviewApprovedEvent;

/**
 * 作品审核通过通知：类型必须落在支持的枚举内，否则投递会被类型校验拒绝。
 *
 * 审核分类已并入系统，这里锁住 SYSTEM 口径，避免再次写回已移除的 REVIEW。
 */
class ReviewApprovedNotificationListenerTest
{
    private final UserNotificationAdminService notifications =
            Mockito.mock(UserNotificationAdminService.class);
    private final ReviewApprovedNotificationListener listener =
            new ReviewApprovedNotificationListener(notifications);

    @Test
    void approvedWorkNoticeUsesSystemType()
    {
        listener.onReviewApproved(new ReviewApprovedEvent(97L, 121L, 88L, "忍界大战"));

        verify(notifications).create(
                eq("REV-APPROVED-97"),
                eq("SYSTEM"),
                eq("作品审核通过"),
                contains("《忍界大战》"),
                eq("WORK"),
                eq("121"),
                eq(List.of(88L)),
                eq("system"));
    }

    @Test
    void incompleteEventIsIgnored()
    {
        listener.onReviewApproved(new ReviewApprovedEvent(1L, 2L, null, "标题"));

        verify(notifications, never()).create(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), Mockito.anyList(), Mockito.anyString());
    }
}
