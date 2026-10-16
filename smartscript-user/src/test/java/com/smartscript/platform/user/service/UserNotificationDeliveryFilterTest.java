package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import com.smartscript.platform.user.domain.admin.AdminNotification;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.AdminNotificationMapper;

/**
 * 投递时按「站内信 × 类型」偏好过滤收件人（规格 §8.6）。
 *
 * 关键不变量：
 *   - 未落库偏好的组合默认开启，照常投递；
 *   - 显式关闭该类型的收件人被剔除，不落收件记录、不计入 receiverCount；
 *   - 全部收件人都关闭时，消息主体仍按幂等键保留，只是没有收件记录；
 *   - 幂等签名按「请求的收件人集合」计算，不随偏好变化——否则用户在两次
 *     等价请求之间改偏好会被误判为同键不同内容（409）；
 *   - 收件人合法性校验先于过滤，非法 id 不得因偏好数据而漏网。
 */
class UserNotificationDeliveryFilterTest
{
    private final AdminNotificationMapper mapper = Mockito.mock(AdminNotificationMapper.class);
    private final UserNotificationAdminService service =
            new UserNotificationAdminService(mapper, transactionManager());

    /** 本测试不触发并发冲突分支，事务管理器只需满足构造契约。 */
    private static PlatformTransactionManager transactionManager()
    {
        return Mockito.mock(PlatformTransactionManager.class);
    }

    /** 幂等键未占用 + 收件人合法 + 主键回填，构成一次干净的创建。 */
    private void stubValidCreation()
    {
        when(mapper.selectByRequestId(anyString())).thenReturn(null);
        when(mapper.countInvalidReceivers(anyList())).thenReturn(0);
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, AdminNotification.class).setNotificationId(777L);
            return 1;
        }).when(mapper).insertNotification(any(AdminNotification.class));
    }

    @Test
    void deliversToAllWhenNoPreferenceClosed()
    {
        stubValidCreation();
        when(mapper.selectInboxDisabledUserIds(anyList(), eq("SYSTEM"))).thenReturn(List.of());

        UserNotificationAdminService.CreateResult result = service.create(
                "req-1", "SYSTEM", "标题", "正文", null, null,
                List.of(10L, 11L, 12L), "admin");

        assertEquals(3, result.getReceiverCount());
        verify(mapper).insertReceivers(eq(777L), eq(List.of(10L, 11L, 12L)));
    }

    @Test
    void excludesReceiversWhoClosedInboxForThisType()
    {
        stubValidCreation();
        when(mapper.selectInboxDisabledUserIds(anyList(), eq("REVIEW"))).thenReturn(List.of(11L));

        UserNotificationAdminService.CreateResult result = service.create(
                "req-2", "REVIEW", "标题", "正文", null, null,
                List.of(10L, 11L, 12L), "admin");

        assertEquals(2, result.getReceiverCount());
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertReceivers(eq(777L), captor.capture());
        assertEquals(List.of(10L, 12L), captor.getValue());
    }

    @Test
    void allClosedKeepsMessageButWritesNoReceiver()
    {
        stubValidCreation();
        when(mapper.selectInboxDisabledUserIds(anyList(), eq("BENEFIT"))).thenReturn(List.of(10L, 11L));

        UserNotificationAdminService.CreateResult result = service.create(
                "req-3", "BENEFIT", "标题", "正文", null, null,
                List.of(10L, 11L), "admin");

        assertEquals(0, result.getReceiverCount());
        verify(mapper, never()).insertReceivers(any(), anyList());
        // 消息主体仍写入：幂等键占用，等价重试不得重复创建
        verify(mapper).insertNotification(any(AdminNotification.class));
        assertTrue(result.isCreated());
    }

    /** 过滤按消息类型精确下发，服务层不对渠道/类型做额外推断。 */
    @Test
    void filteringQueryReceivesRequestedType()
    {
        stubValidCreation();
        when(mapper.selectInboxDisabledUserIds(anyList(), anyString())).thenReturn(List.of());

        service.create("req-4", "TRANSACTION", "标题", "正文", null, null,
                List.of(10L), "admin");

        verify(mapper).selectInboxDisabledUserIds(eq(List.of(10L)), eq("TRANSACTION"));
    }

    /** 非法收件人整体失败，且不进入投递过滤：校验先于过滤。 */
    @Test
    void invalidReceiverFailsBeforeFiltering()
    {
        when(mapper.selectByRequestId(anyString())).thenReturn(null);
        when(mapper.countInvalidReceivers(anyList())).thenReturn(1);

        try
        {
            service.create("req-5", "SYSTEM", "标题", "正文", null, null,
                    List.of(10L), "admin");
            throw new AssertionError("expected invalid receiver to fail creation");
        }
        catch (AppAdminException expected)
        {
            // 预期：非法收件人整体失败
        }
        verify(mapper, never()).selectInboxDisabledUserIds(anyList(), anyString());
        verify(mapper, never()).insertNotification(any(AdminNotification.class));
    }
}
