package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandStage;
import com.smartscript.platform.trade.mapper.SysDemandMapper;
import com.smartscript.platform.trade.mapper.SysDemandStageMapper;

/**
 * 本轮交付「临近截止」提醒的扫描口径测试。
 *
 * <p>提醒挂在既有的 10 秒任务上（不新增 sys_job 行），所以每次扫描必须只落一条：
 * 进入 2 小时窗口后不再重复发 24 小时档，超出全部档位则一条都不发。
 * 落库幂等由 TradeNotifyService 的 requestId 保证，本层只管「发哪一档、发不发」。
 */
class TradeDemandDueSoonReminderTest
{
    private static final long DEMAND_ID = 56L;
    private static final long STAGE_ID = 80L;
    private static final long CREATOR_ID = 9092L;
    private static final int STAGE_ORDER = 2;
    private static final long HOUR = 3_600_000L;

    private SysDemandMapper demandMapper;
    private SysDemandStageMapper stageMapper;
    private TradeNotifyService notifyService;
    private TradeDemandService service;

    @BeforeEach
    void setUp()
    {
        demandMapper = mock(SysDemandMapper.class);
        stageMapper = mock(SysDemandStageMapper.class);
        notifyService = mock(TradeNotifyService.class);

        service = new TradeDemandService();
        ReflectionTestUtils.setField(service, "demandMapper", demandMapper);
        ReflectionTestUtils.setField(service, "stageMapper", stageMapper);
        ReflectionTestUtils.setField(service, "notifyService", notifyService);
    }

    private SysDemand demand(String status, Long creatorId)
    {
        SysDemand d = new SysDemand();
        d.setDemandId(DEMAND_ID);
        d.setTitle("征集：短剧脚本");
        d.setStatus(status);
        d.setAcceptedCreatorId(creatorId);
        return d;
    }

    private SysDemandStage stageDueIn(long millis)
    {
        SysDemandStage s = new SysDemandStage();
        s.setStageId(STAGE_ID);
        s.setDemandId(DEMAND_ID);
        s.setStageOrder(STAGE_ORDER);
        s.setAmount(new BigDecimal("1800.00"));
        s.setStatus(SysDemandStage.STAGE_STATUS_PENDING);
        s.setDueAt(new Date(System.currentTimeMillis() + millis));
        return s;
    }

    private void stubScan(SysDemandStage stage, SysDemand demand)
    {
        when(stageMapper.selectDueSoonActiveStages(anyLong())).thenReturn(List.of(stage));
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(demand);
    }

    @Test
    @DisplayName("剩余 20 小时：只发 24 小时档一条")
    void within24hHours_notifiesWideTierOnly()
    {
        SysDemandStage stage = stageDueIn(20 * HOUR);
        SysDemand d = demand(TradeDemandService.DEMAND_STATUS_IN_PROGRESS, CREATOR_ID);
        stubScan(stage, d);

        assertEquals(1, service.remindDueSoonRounds());

        verify(notifyService, times(1)).notifyStageDueSoon(eq(d), eq(stage), eq(24), anyLong());
        // 扫描窗口取最宽档位，一次 SQL 覆盖两档
        verify(stageMapper).selectDueSoonActiveStages(eq(24 * 60L));
    }

    @Test
    @DisplayName("剩余 50 分钟：只发最窄的 2 小时档，不补发 24 小时档")
    void within2hHours_notifiesTightestTierOnly()
    {
        SysDemandStage stage = stageDueIn(50 * 60_000L);
        SysDemand d = demand(TradeDemandService.DEMAND_STATUS_IN_PROGRESS, CREATOR_ID);
        stubScan(stage, d);

        assertEquals(1, service.remindDueSoonRounds());

        verify(notifyService, times(1)).notifyStageDueSoon(eq(d), eq(stage), eq(2), anyLong());
    }

    @Test
    @DisplayName("档位外的轮次不发提醒（SQL 窗口与代码判定双重收口）")
    void outsideAllTiers_notifiesNothing()
    {
        SysDemandStage stage = stageDueIn(30 * HOUR);
        stubScan(stage, demand(TradeDemandService.DEMAND_STATUS_IN_PROGRESS, CREATOR_ID));

        assertEquals(0, service.remindDueSoonRounds());

        verify(notifyService, never()).notifyStageDueSoon(
                any(SysDemand.class), any(SysDemandStage.class), anyInt(), anyLong());
    }

    @Test
    @DisplayName("任务已不在进行中或无人接单：不发提醒")
    void staleOrUnclaimedDemand_notifiesNothing()
    {
        SysDemandStage stage = stageDueIn(60 * 60_000L);
        stubScan(stage, demand(TradeDemandService.DEMAND_STATUS_FAILED, CREATOR_ID));
        assertEquals(0, service.remindDueSoonRounds());

        stubScan(stage, demand(TradeDemandService.DEMAND_STATUS_IN_PROGRESS, null));
        assertEquals(0, service.remindDueSoonRounds());

        verify(notifyService, never()).notifyStageDueSoon(
                any(SysDemand.class), any(SysDemandStage.class), anyInt(), anyLong());
    }

    @Test
    @DisplayName("剩余时长按分钟向上取整后交给文案，不足一分钟也按 1 分钟")
    void remainingMinutes_ceilToMinute()
    {
        SysDemandStage stage = stageDueIn(90 * 60_000L + 1_000L);
        SysDemand d = demand(TradeDemandService.DEMAND_STATUS_IN_PROGRESS, CREATOR_ID);
        stubScan(stage, d);

        service.remindDueSoonRounds();

        verify(notifyService).notifyStageDueSoon(eq(d), eq(stage), eq(2), eq(91L));
    }

    @Test
    @DisplayName("单条轮次投递抛错不中断整批扫描，抛错那条不计数")
    void oneFailure_doesNotStopScan()
    {
        SysDemandStage first = stageDueIn(60 * 60_000L);
        SysDemandStage second = stageDueIn(20 * HOUR);
        SysDemand d = demand(TradeDemandService.DEMAND_STATUS_IN_PROGRESS, CREATOR_ID);
        when(stageMapper.selectDueSoonActiveStages(anyLong())).thenReturn(List.of(first, second));
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(d);
        doThrow(new IllegalStateException("boom")).when(notifyService)
                .notifyStageDueSoon(eq(d), eq(first), anyInt(), anyLong());

        assertEquals(1, service.remindDueSoonRounds());

        verify(notifyService, times(1)).notifyStageDueSoon(eq(d), eq(first), eq(2), anyLong());
        verify(notifyService, times(1)).notifyStageDueSoon(eq(d), eq(second), eq(24), anyLong());
    }
}
