package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.smartscript.platform.user.domain.admin.RealNameApplication;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.RealNameAdminMapper;

/**
 * 实名审核通过/驳回时向申请人投递站内通知。
 *
 * 关键不变量：决定成功后按申请号幂等投递一条 REVIEW 通知；决定失败（并发冲突）
 * 不投递；通知投递失败不影响审核结果本身。
 */
class RealNameReviewNotificationTest
{
    private final RealNameAdminMapper mapper = Mockito.mock(RealNameAdminMapper.class);
    private final UserNotificationAdminService notifications = Mockito.mock(UserNotificationAdminService.class);
    private final MaterialAccessTokenService materialTokenService = Mockito.mock(MaterialAccessTokenService.class);
    private final RealNameReviewService service =
            new RealNameReviewService(mapper, notifications, materialTokenService);

    @Test
    void approveDeliversReviewNoticeToApplicant()
    {
        when(mapper.selectApplicationStatus(200L)).thenReturn("PENDING");
        when(mapper.decideIfPending(eq(200L), eq("PENDING"), eq("APPROVED"), eq(1L), isNull())).thenReturn(1);
        when(mapper.selectApplicationUserId(200L)).thenReturn(9100L);

        RealNameApplication updated = service.decide(200L, "APPROVE", null, 1L, "PENDING");

        assertEquals("APPROVED", updated.getStatus());
        verify(notifications).create(
                eq("realname-review-200"),
                eq("REVIEW"),
                eq("实名认证已通过"),
                anyString(),
                eq("REAL_NAME"),
                eq("200"),
                eq(List.of(9100L)),
                eq("system"));
    }

    @Test
    void rejectDeliversNoticeWithReason()
    {
        when(mapper.selectApplicationStatus(201L)).thenReturn("PENDING");
        when(mapper.decideIfPending(eq(201L), eq("PENDING"), eq("REJECTED"), eq(1L), eq("照片不清晰"))).thenReturn(1);
        when(mapper.selectApplicationUserId(201L)).thenReturn(9101L);

        service.decide(201L, "REJECT", "照片不清晰", 1L, "PENDING");

        verify(notifications).create(
                eq("realname-review-201"),
                eq("REVIEW"),
                eq("实名认证未通过"),
                org.mockito.ArgumentMatchers.contains("照片不清晰"),
                eq("REAL_NAME"),
                eq("201"),
                eq(List.of(9101L)),
                eq("system"));
    }

    @Test
    void conflictDeliversNoNotice()
    {
        when(mapper.selectApplicationStatus(202L)).thenReturn("PENDING");
        when(mapper.decideIfPending(eq(202L), eq("PENDING"), eq("APPROVED"), eq(1L), isNull())).thenReturn(0);

        try
        {
            service.decide(202L, "APPROVE", null, 1L, "PENDING");
            throw new AssertionError("expected conflict");
        }
        catch (AppAdminException expected)
        {
            // 并发冲突：另一决定已抢先成功
        }
        verify(notifications, never()).create(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), Mockito.anyList(), anyString());
    }

    @Test
    void noticeFailureDoesNotBreakDecision()
    {
        when(mapper.selectApplicationStatus(203L)).thenReturn("PENDING");
        when(mapper.decideIfPending(eq(203L), eq("PENDING"), eq("APPROVED"), eq(1L), isNull())).thenReturn(1);
        when(mapper.selectApplicationUserId(203L)).thenReturn(9102L);
        when(notifications.create(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), Mockito.anyList(), anyString())).thenThrow(new RuntimeException("boom"));

        RealNameApplication updated = service.decide(203L, "APPROVE", null, 1L, "PENDING");

        assertNotNull(updated);
        assertEquals("APPROVED", updated.getStatus());
    }
}
