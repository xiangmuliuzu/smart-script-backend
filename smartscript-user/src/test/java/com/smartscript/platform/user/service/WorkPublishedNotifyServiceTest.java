package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import com.smartscript.platform.user.domain.admin.AdminNotification;
import com.smartscript.platform.user.mapper.AdminNotificationMapper;
import com.smartscript.platform.user.mapper.UserFollowMapper;

/**
 * 关注动态投递：粉丝圈定、偏好过滤、幂等与分批写入。
 *
 * 关键不变量：
 *   - 同一作品只投递一次（requestId = WORK-PUB-{workId}）；
 *   - 站内信 × FOLLOW 关闭者不落收件记录；全部关闭时消息主体仍按幂等键保留；
 *   - 收件记录按 500 一批写入，不产生超长 SQL；
 *   - 消息带 WORK/{workId} 业务引用，audience=APP。
 */
class WorkPublishedNotifyServiceTest
{
    private final AdminNotificationMapper notificationMapper = Mockito.mock(AdminNotificationMapper.class);
    private final UserFollowMapper followMapper = Mockito.mock(UserFollowMapper.class);
    private final WorkPublishedNotifyService service =
            new WorkPublishedNotifyService(notificationMapper, followMapper);

    /** 主键回填，模拟 useGeneratedKeys。 */
    private void stubInsert(long notificationId)
    {
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, AdminNotification.class).setNotificationId(notificationId);
            return 1;
        }).when(notificationMapper).insertNotification(any(AdminNotification.class));
    }

    private AdminNotification captureNotification()
    {
        ArgumentCaptor<AdminNotification> captor = ArgumentCaptor.forClass(AdminNotification.class);
        verify(notificationMapper).insertNotification(captor.capture());
        return captor.getValue();
    }

    @Test
    void notifiesAllFollowersWithWorkReference()
    {
        when(notificationMapper.selectByRequestId("WORK-PUB-301")).thenReturn(null);
        when(followMapper.selectWorkPublishFollowerIds(88L)).thenReturn(List.of(10L, 11L));
        when(notificationMapper.selectInboxDisabledUserIds(anyList(), eq("FOLLOW"))).thenReturn(List.of());
        stubInsert(777L);

        service.notifyFollowers(301L, 88L, "悬疑故事", "作者甲");

        AdminNotification entity = captureNotification();
        assertEquals("FOLLOW", entity.getType());
        assertEquals("「作者甲」发布了新作品", entity.getTitle());
        assertEquals("WORK", entity.getBusinessType());
        assertEquals("301", entity.getBusinessId());
        assertNull(entity.getAudience());
        assertEquals("system", entity.getCreatedBy());
        verify(notificationMapper).insertEventReceivers(777L, List.of(10L, 11L), "system");
    }

    @Test
    void sameWorkIsNotNotifiedTwice()
    {
        when(notificationMapper.selectByRequestId("WORK-PUB-301"))
                .thenReturn(new AdminNotification());

        service.notifyFollowers(301L, 88L, "悬疑故事", "作者甲");

        verify(followMapper, never()).selectWorkPublishFollowerIds(any());
        verify(notificationMapper, never()).insertNotification(any());
    }

    @Test
    void excludesFollowersWhoClosedFollowInboxPreference()
    {
        when(notificationMapper.selectByRequestId("WORK-PUB-302")).thenReturn(null);
        when(followMapper.selectWorkPublishFollowerIds(88L)).thenReturn(List.of(10L, 11L, 12L));
        when(notificationMapper.selectInboxDisabledUserIds(anyList(), eq("FOLLOW"))).thenReturn(List.of(11L));
        stubInsert(778L);

        service.notifyFollowers(302L, 88L, "新作", "作者甲");

        verify(notificationMapper).insertEventReceivers(778L, List.of(10L, 12L), "system");
    }

    @Test
    void keepsMessageBodyButWritesNoReceiverWhenAllClosed()
    {
        when(notificationMapper.selectByRequestId("WORK-PUB-303")).thenReturn(null);
        when(followMapper.selectWorkPublishFollowerIds(88L)).thenReturn(List.of(10L, 11L));
        when(notificationMapper.selectInboxDisabledUserIds(anyList(), eq("FOLLOW"))).thenReturn(List.of(10L, 11L));
        stubInsert(779L);

        service.notifyFollowers(303L, 88L, "新作", "作者甲");

        verify(notificationMapper).insertNotification(any(AdminNotification.class));
        verify(notificationMapper, never()).insertEventReceivers(any(), anyList(), anyString());
    }

    @Test
    void noFollowersWritesNothing()
    {
        when(notificationMapper.selectByRequestId("WORK-PUB-304")).thenReturn(null);
        when(followMapper.selectWorkPublishFollowerIds(88L)).thenReturn(List.of());

        service.notifyFollowers(304L, 88L, "新作", "作者甲");

        verify(notificationMapper, never()).insertNotification(any());
    }

    @Test
    void receiversAreWrittenInChunksOf500()
    {
        when(notificationMapper.selectByRequestId("WORK-PUB-305")).thenReturn(null);
        List<Long> followers = new ArrayList<>();
        for (long id = 1; id <= 501; id++)
        {
            followers.add(id);
        }
        when(followMapper.selectWorkPublishFollowerIds(88L)).thenReturn(followers);
        when(notificationMapper.selectInboxDisabledUserIds(anyList(), eq("FOLLOW"))).thenReturn(List.of());
        stubInsert(780L);

        service.notifyFollowers(305L, 88L, "新作", "作者甲");

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationMapper, times(2)).insertEventReceivers(eq(780L), captor.capture(), eq("system"));
        assertEquals(500, captor.getAllValues().get(0).size());
        assertEquals(1, captor.getAllValues().get(1).size());
        assertEquals(501L, captor.getAllValues().get(1).get(0));
    }

    @Test
    void concurrentDuplicateInsertDoesNotWriteReceivers()
    {
        when(notificationMapper.selectByRequestId("WORK-PUB-306")).thenReturn(null);
        when(followMapper.selectWorkPublishFollowerIds(88L)).thenReturn(List.of(10L));
        when(notificationMapper.selectInboxDisabledUserIds(anyList(), eq("FOLLOW"))).thenReturn(List.of());
        when(notificationMapper.insertNotification(any(AdminNotification.class)))
                .thenThrow(new DuplicateKeyException("uk_user_notification_request_id"));

        service.notifyFollowers(306L, 88L, "新作", "作者甲");

        verify(notificationMapper, never()).insertEventReceivers(any(), anyList(), anyString());
    }

    @Test
    void contentOmitsMissingAuthorAndTitle()
    {
        assertEquals("关注的创作者发布了新作品", WorkPublishedNotifyService.titleOf(null));
        assertEquals("您关注的创作者发布了新作品。\n可前往作品详情查看。",
                WorkPublishedNotifyService.contentOf(" ", null));
        assertEquals("您关注的创作者「作者甲」发布了新作品。\n· 作品：《新作》\n可前往作品详情查看。",
                WorkPublishedNotifyService.contentOf(" 作者甲 ", " 新作 "));
    }
}
