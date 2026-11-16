package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.ruoyi.framework.realtime.RealtimePushService;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.mapper.AdminNoticeMapper;
import com.smartscript.platform.trade.mapper.SysDemandMapper;
import com.smartscript.platform.user.service.UserNotificationAdminService;

/**
 * 征集到点关闭 + 运营侧公告投递单元测试。
 *
 * 发布权收归平台后，过期与关闭都必须让运营在管理端收件箱看到：
 *   E7 定时扫描把过截止仍挂 open 的单自动关闭并投递公告；
 *   E8 运营手动关闭同样投递公告（其他运营需要知道该单已不再征集）；
 *   投递走 sys_notice(audience='ADMIN')，键位与业务一一对应，重复触发不刷屏。
 */
class DemandRecruitExpiryCloseTest
{
    private static final long DEMAND_ID = 71L;
    private static final long OTHER_ID = 72L;

    private SysDemandMapper demandMapper;
    private TradeNotifyService notifyService;
    private TradeDemandService service;

    @BeforeEach
    void setUp()
    {
        demandMapper = mock(SysDemandMapper.class);
        notifyService = mock(TradeNotifyService.class);

        service = new TradeDemandService();
        ReflectionTestUtils.setField(service, "demandMapper", demandMapper);
        ReflectionTestUtils.setField(service, "notifyService", notifyService);
        // expireOverdueRounds 另两段扫描在本用例里保持空转
        ReflectionTestUtils.setField(service, "extendApplyMapper",
                mock(com.smartscript.platform.trade.mapper.SysDemandExtendApplyMapper.class));
        ReflectionTestUtils.setField(service, "stageMapper",
                mock(com.smartscript.platform.trade.mapper.SysDemandStageMapper.class));
    }

    private SysDemand expiredRecruit(long id)
    {
        SysDemand d = new SysDemand();
        d.setDemandId(id);
        d.setDemandNo("DM-" + id);
        d.setTitle("过期征集-" + id);
        d.setStatus(TradeDemandService.DEMAND_STATUS_OPEN);
        d.setDeadline(new Date(System.currentTimeMillis() - 86_400_000L));
        return d;
    }

    @Test
    @DisplayName("E7 定时扫描：过截止的 open 单逐条 CAS 关闭并投递运营公告")
    void closeExpiredRecruits_closesAndNotifies()
    {
        SysDemand first = expiredRecruit(DEMAND_ID);
        SysDemand second = expiredRecruit(OTHER_ID);
        when(demandMapper.selectExpiredOpenDemands()).thenReturn(List.of(first, second));
        when(demandMapper.updateStatusCas(any(), eq(TradeDemandService.DEMAND_STATUS_CLOSED),
                eq("system"), eq(TradeDemandService.DEMAND_STATUS_OPEN))).thenReturn(1);

        assertEquals(2, service.closeExpiredRecruits());

        verify(demandMapper, times(1)).updateStatusCas(eq(DEMAND_ID),
                eq(TradeDemandService.DEMAND_STATUS_CLOSED), eq("system"),
                eq(TradeDemandService.DEMAND_STATUS_OPEN));
        verify(demandMapper, times(1)).updateStatusCas(eq(OTHER_ID),
                eq(TradeDemandService.DEMAND_STATUS_CLOSED), eq("system"),
                eq(TradeDemandService.DEMAND_STATUS_OPEN));
        verify(notifyService, times(1)).notifyDemandClosed(eq(first), eq(true));
        verify(notifyService, times(1)).notifyDemandClosed(eq(second), eq(true));
    }

    @Test
    @DisplayName("E7 并发防重：CAS 落败（已被接单或已关闭）不改状态也不发公告")
    void closeExpiredRecruits_lostCas_noNotify()
    {
        when(demandMapper.selectExpiredOpenDemands()).thenReturn(List.of(expiredRecruit(DEMAND_ID)));
        when(demandMapper.updateStatusCas(any(), any(), any(), any())).thenReturn(0);

        assertEquals(0, service.closeExpiredRecruits());

        verify(notifyService, never()).notifyDemandClosed(any(SysDemand.class), anyBoolean());
    }

    @Test
    @DisplayName("E7 单条异常只记日志：整批不中断，剩余过期单继续关闭")
    void closeExpiredRecruits_singleFailureKeepsGoing()
    {
        when(demandMapper.selectExpiredOpenDemands()).thenReturn(List.of(expiredRecruit(DEMAND_ID),
                expiredRecruit(OTHER_ID)));
        when(demandMapper.updateStatusCas(eq(DEMAND_ID), any(), any(), any()))
                .thenThrow(new RuntimeException("db down"));
        when(demandMapper.updateStatusCas(eq(OTHER_ID), any(), any(), any())).thenReturn(1);

        assertEquals(1, service.closeExpiredRecruits());

        verify(notifyService, times(1)).notifyDemandClosed(any(SysDemand.class), eq(true));
    }

    @Test
    @DisplayName("定时入口把过期征集关闭数计入处置总数")
    void expireOverdueRounds_includesClosedRecruits()
    {
        when(demandMapper.selectExpiredOpenDemands()).thenReturn(List.of(expiredRecruit(DEMAND_ID)));
        when(demandMapper.updateStatusCas(any(), any(), any(), any())).thenReturn(1);

        assertEquals(1, service.expireOverdueRounds());
    }

    @Test
    @DisplayName("E8 运营手动关闭：状态落库后投递公告，标明是手动截止")
    void closeDemand_notifiesManualClosure()
    {
        SysDemand d = expiredRecruit(DEMAND_ID);
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(d);
        when(demandMapper.updateStatusCas(eq(DEMAND_ID), eq(TradeDemandService.DEMAND_STATUS_CLOSED),
                anyString(), eq(TradeDemandService.DEMAND_STATUS_OPEN))).thenReturn(1);

        assertEquals(1, service.closeDemand(DEMAND_ID, 1L));

        verify(notifyService, times(1)).notifyDemandClosed(eq(d), eq(false));
    }

    @Test
    @DisplayName("E8 关闭前置校验：非 open 不可关闭，也不投递公告")
    void closeDemand_rejectsNonOpen()
    {
        SysDemand d = expiredRecruit(DEMAND_ID);
        d.setStatus(TradeDemandService.DEMAND_STATUS_CLOSED);
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(d);

        assertThrows(RuntimeException.class, () -> service.closeDemand(DEMAND_ID, 1L));

        verify(demandMapper, never()).updateStatusCas(any(), any(), any(), any());
        verify(notifyService, never()).notifyDemandClosed(any(SysDemand.class), anyBoolean());
    }

    @Test
    @DisplayName("截止口径：deadline 只到天，截止日当天整天仍可接，过完当天才算过期")
    void isRecruitExpired_treatsDeadlineDayAsInclusive()
    {
        SysDemand d = expiredRecruit(DEMAND_ID);

        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);

        d.setDeadline(todayStart.getTime());
        org.junit.jupiter.api.Assertions.assertFalse(TradeDemandService.isRecruitExpired(d));

        todayStart.add(Calendar.DAY_OF_MONTH, -1);
        d.setDeadline(todayStart.getTime());
        org.junit.jupiter.api.Assertions.assertTrue(TradeDemandService.isRecruitExpired(d));

        todayStart.add(Calendar.DAY_OF_MONTH, 2);
        d.setDeadline(todayStart.getTime());
        org.junit.jupiter.api.Assertions.assertFalse(TradeDemandService.isRecruitExpired(d));

        d.setDeadline(null);
        org.junit.jupiter.api.Assertions.assertFalse(TradeDemandService.isRecruitExpired(d),
                "不设时限的征集永不过期");
    }

    @Test
    @DisplayName("公告投递写 sys_notice(audience=ADMIN)：幂等键区分自动/手动，重复触发由 SQL 侧收敛")
    void notifyDemandClosed_writesAdminNotice()
    {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AdminNoticeMapper noticeMapper = mock(AdminNoticeMapper.class);
        TradeNotifyService real = new TradeNotifyService(
                mock(UserNotificationAdminService.class), noticeMapper, tm);

        SysDemand d = expiredRecruit(DEMAND_ID);
        real.notifyDemandClosed(d, true);

        verify(noticeMapper, times(1)).insertIfAbsent(eq("TRD-DM-CLOSE-71-AUTO"),
                eq("任务征集已自动关闭"), contains("DM-71"));

        real.notifyDemandClosed(d, false);

        verify(noticeMapper, times(1)).insertIfAbsent(eq("TRD-DM-CLOSE-71-MANUAL"),
                eq("任务征集已截止"), contains("DM-71"));
    }

    @Test
    @DisplayName("公告投递失败只告警，不影响关闭主流程")
    void notifyDemandClosed_deliveryFailureDoesNotBreakBusiness()
    {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AdminNoticeMapper noticeMapper = mock(AdminNoticeMapper.class);
        when(noticeMapper.insertIfAbsent(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("公告表写入失败"));
        TradeNotifyService real = new TradeNotifyService(
                mock(UserNotificationAdminService.class), noticeMapper, tm);

        real.notifyDemandClosed(expiredRecruit(DEMAND_ID), true);

        verify(noticeMapper, times(1)).insertIfAbsent(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("待验收/延时申请等运营事件改投管理端公告，不再投递运营读不到的 App 收件箱")
    void operatorSideEvents_goToAdminNotice()
    {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AdminNoticeMapper noticeMapper = mock(AdminNoticeMapper.class);
        UserNotificationAdminService inbox = mock(UserNotificationAdminService.class);
        TradeNotifyService real = new TradeNotifyService(inbox, noticeMapper, tm);

        SysDemand d = expiredRecruit(DEMAND_ID);
        d.setAcceptedCreatorId(9L);
        com.smartscript.platform.trade.domain.SysDemandStage stage =
                new com.smartscript.platform.trade.domain.SysDemandStage();
        stage.setStageOrder(1);
        stage.setSubmittedAt(new Date());

        real.notifyStageReadyForReview(d, stage);
        real.notifyExtendRequested(5L, d, 1, 1, 0, "取材需要");
        real.notifyDemandClaimed(d);

        verify(noticeMapper, times(3)).insertIfAbsent(anyString(), anyString(), anyString());
        verify(inbox, never()).create(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("公告真正落库才发实时帧：收件人是后台账号，运营红点不必等下一轮轮询")
    void notifyDemandClosed_pushesRealtimeFrameOnNewNotice()
    {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AdminNoticeMapper noticeMapper = mock(AdminNoticeMapper.class);
        when(noticeMapper.insertIfAbsent(anyString(), anyString(), anyString())).thenReturn(1);
        when(noticeMapper.selectAdminRecipients()).thenReturn(List.of(1L, 2L));
        RealtimePushService push = mock(RealtimePushService.class);
        TradeNotifyService real = new TradeNotifyService(
                mock(UserNotificationAdminService.class), noticeMapper, tm);
        ReflectionTestUtils.setField(real, "realtimePush", push);

        real.notifyDemandClosed(expiredRecruit(DEMAND_ID), true);

        verify(push, times(1)).pushNotification(any(), eq("ADMIN_NOTICE"),
                eq("任务征集已自动关闭"), eq(List.of(1L, 2L)));
    }

    @Test
    @DisplayName("幂等命中（未新增）不发帧；推送异常只告警，不影响关闭主流程")
    void notifyDemandClosed_realtimeNeitherSpamsNorBreaksBusiness()
    {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AdminNoticeMapper deduped = mock(AdminNoticeMapper.class);
        when(deduped.insertIfAbsent(anyString(), anyString(), anyString())).thenReturn(0);
        RealtimePushService push = mock(RealtimePushService.class);
        TradeNotifyService dedupedService = new TradeNotifyService(
                mock(UserNotificationAdminService.class), deduped, tm);
        ReflectionTestUtils.setField(dedupedService, "realtimePush", push);

        dedupedService.notifyDemandClosed(expiredRecruit(DEMAND_ID), true);

        verify(push, never()).pushNotification(any(), anyString(), anyString(), any());

        AdminNoticeMapper broken = mock(AdminNoticeMapper.class);
        when(broken.insertIfAbsent(anyString(), anyString(), anyString())).thenReturn(1);
        when(broken.selectAdminRecipients()).thenThrow(new RuntimeException("收件人查询失败"));
        RealtimePushService push2 = mock(RealtimePushService.class);
        TradeNotifyService brokenService = new TradeNotifyService(
                mock(UserNotificationAdminService.class), broken, tm);
        ReflectionTestUtils.setField(brokenService, "realtimePush", push2);

        brokenService.notifyDemandClosed(expiredRecruit(DEMAND_ID), true);

        verify(broken, times(1)).insertIfAbsent(anyString(), anyString(), anyString());
        verify(push2, never()).pushNotification(any(), anyString(), anyString(), any());
    }
}
