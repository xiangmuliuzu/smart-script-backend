package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.smartscript.platform.user.domain.admin.CreatorApplication;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.CreatorApplicationAdminMapper;

/**
 * 创作者申请审核：通过/驳回的结果、自动开通作者能力、站内通知与并发冲突处理。
 *
 * 关键不变量：APPROVE 在同一事务内幂等开通作者能力并投递 REVIEW 通知；REJECT 不开通能力、
 * 通知带驳回原因；并发冲突（decideIfPending=0）不产生能力变更与通知；通知失败不影响审核结果。
 */
class CreatorApplicationReviewServiceTest
{
    private final CreatorApplicationAdminMapper mapper = Mockito.mock(CreatorApplicationAdminMapper.class);
    private final UserNotificationAdminService notifications = Mockito.mock(UserNotificationAdminService.class);
    private final AuthorCapabilityService authorCapabilityService = Mockito.mock(AuthorCapabilityService.class);
    private final CreatorApplicationReviewService service =
            new CreatorApplicationReviewService(mapper, notifications, authorCapabilityService);

    @Test
    void approveGrantsCapabilityAndNotifies()
    {
        when(mapper.selectApplicationStatus(200L)).thenReturn("PENDING");
        when(mapper.decideIfPending(eq(200L), eq("PENDING"), eq("APPROVED"), eq(1L), isNull())).thenReturn(1);
        when(mapper.selectApplicationUserId(200L)).thenReturn(9100L);

        CreatorApplication updated = service.decide(200L, "APPROVE", null, 1L, "运维员", "PENDING");

        assertEquals("APPROVED", updated.getStatus());
        // 通过即开通作者能力（同一事务）
        verify(authorCapabilityService).setEnabled(eq(9100L), eq(true), contains("创作者申请审核通过"), eq(1L), eq("运维员"));
        // 投递 REVIEW 站内通知
        verify(notifications).create(
                eq("creatorapply-review-200"), eq("REVIEW"), eq("创作者申请已通过"),
                anyString(), eq("CREATOR_APPLY"), eq("200"), eq(List.of(9100L)), eq("system"));
    }

    @Test
    void rejectDoesNotGrantCapabilityAndNotifiesReason()
    {
        when(mapper.selectApplicationStatus(201L)).thenReturn("PENDING");
        when(mapper.decideIfPending(eq(201L), eq("PENDING"), eq("REJECTED"), eq(1L), eq("材料不足"))).thenReturn(1);
        when(mapper.selectApplicationUserId(201L)).thenReturn(9101L);

        service.decide(201L, "REJECT", "材料不足", 1L, "运维员", "PENDING");

        verify(authorCapabilityService, never()).setEnabled(Mockito.anyLong(), Mockito.anyBoolean(),
                anyString(), Mockito.anyLong(), anyString());
        verify(notifications).create(
                eq("creatorapply-review-201"), eq("REVIEW"), eq("创作者申请未通过"),
                contains("材料不足"), eq("CREATOR_APPLY"), eq("201"), eq(List.of(9101L)), eq("system"));
    }

    @Test
    void conflictNeitherGrantsNorNotifies()
    {
        when(mapper.selectApplicationStatus(202L)).thenReturn("PENDING");
        when(mapper.decideIfPending(eq(202L), eq("PENDING"), eq("APPROVED"), eq(1L), isNull())).thenReturn(0);

        try
        {
            service.decide(202L, "APPROVE", null, 1L, "运维员", "PENDING");
            throw new AssertionError("expected conflict");
        }
        catch (AppAdminException expected)
        {
            // 并发冲突：另一决定已抢先成功
        }
        verify(authorCapabilityService, never()).setEnabled(Mockito.anyLong(), Mockito.anyBoolean(),
                anyString(), Mockito.anyLong(), anyString());
        verify(notifications, never()).create(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), Mockito.anyList(), anyString());
    }

    @Test
    void approveWithRejectReasonIsRejected()
    {
        when(mapper.selectApplicationStatus(203L)).thenReturn("PENDING");
        try
        {
            service.decide(203L, "APPROVE", "不应填写原因", 1L, "运维员", "PENDING");
            throw new AssertionError("expected bad request");
        }
        catch (AppAdminException expected)
        {
            assertEquals(400, expected.getHttpStatus());
        }
    }

    @Test
    void noticeFailureDoesNotBreakDecision()
    {
        when(mapper.selectApplicationStatus(204L)).thenReturn("PENDING");
        when(mapper.decideIfPending(eq(204L), eq("PENDING"), eq("APPROVED"), eq(1L), isNull())).thenReturn(1);
        when(mapper.selectApplicationUserId(204L)).thenReturn(9102L);
        when(notifications.create(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), Mockito.anyList(), anyString())).thenThrow(new RuntimeException("boom"));

        CreatorApplication updated = service.decide(204L, "APPROVE", null, 1L, "运维员", "PENDING");

        assertNotNull(updated);
        assertEquals("APPROVED", updated.getStatus());
    }
}
