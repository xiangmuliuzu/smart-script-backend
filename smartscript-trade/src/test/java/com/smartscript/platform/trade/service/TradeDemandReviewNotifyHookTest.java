package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandStage;
import com.smartscript.platform.trade.mapper.SysDemandMapper;
import com.smartscript.platform.trade.mapper.SysDemandStageMapper;

/**
 * 任务（接单制）流程通知挂钩单元测试。
 *
 * 覆盖三条钩子：
 *   E3 人工驳回 → notifyStageRejectedByBuyer（TradeDemandService.reviewStage）
 *   E4 末轮通过整单完成 → notifyDemandCompleted（同上，末轮 else 分支）
 *   E1/E2 AI 审核 pass/fail → notifyStageReadyForReview / notifyStageRejectedByAi
 *        （DemandStageAiReviewService.notifyAiOutcome，通过反射直接触达私有方法）
 *
 * 不测 TradeNotifyService.push 的事务/吞异常，只测调用参数与幂等键位。
 * 方法签名统一为 (SysDemand, SysDemandStage, ...) 域对象入参，本层不再逐字段验证。
 */
class TradeDemandReviewNotifyHookTest
{
    private static final long DEMAND_ID = 56L;
    private static final long STAGE_ID = 80L;
    private static final long BUYER_ID = 9091L;
    private static final long CREATOR_ID = 9092L;
    private static final int STAGE_ORDER = 1;
    private static final String DEMAND_TITLE = "E2E-逐轮放款";
    private static final String REJECT_REASON = "第二轮剧情偏离主题";

    private SysDemandMapper demandMapper;
    private SysDemandStageMapper stageMapper;
    private TradeFinanceService financeService;
    private TradeNotifyService notifyService;
    private TradeDemandService service;

    @BeforeEach
    void setUp()
    {
        demandMapper = mock(SysDemandMapper.class);
        stageMapper = mock(SysDemandStageMapper.class);
        financeService = mock(TradeFinanceService.class);
        notifyService = mock(TradeNotifyService.class);

        service = new TradeDemandService();
        ReflectionTestUtils.setField(service, "demandMapper", demandMapper);
        ReflectionTestUtils.setField(service, "stageMapper", stageMapper);
        ReflectionTestUtils.setField(service, "financeService", financeService);
        ReflectionTestUtils.setField(service, "notifyService", notifyService);
    }

    private SysDemand demand(String status)
    {
        SysDemand d = new SysDemand();
        d.setDemandId(DEMAND_ID);
        d.setTitle(DEMAND_TITLE);
        d.setClientId(BUYER_ID);
        d.setAcceptedCreatorId(CREATOR_ID);
        d.setStatus(status);
        return d;
    }

    private SysDemandStage submittedStage(BigDecimal amount)
    {
        SysDemandStage s = new SysDemandStage();
        s.setStageId(STAGE_ID);
        s.setDemandId(DEMAND_ID);
        s.setStageOrder(STAGE_ORDER);
        s.setStatus(SysDemandStage.STAGE_STATUS_SUBMITTED);
        s.setAmount(amount);
        long now = System.currentTimeMillis();
        s.setSubmittedAt(new Date(now - 60_000L));
        s.setAiReviewedAt(new Date(now - 30_000L));
        s.setDueAt(new Date(now + 3_600_000L));
        return s;
    }

    @Test
    @DisplayName("E3 人工驳回：notifyStageRejectedByBuyer 收到 demand/stage/原因/新截止时间戳")
    void reviewStageReject_notifiesCreator()
    {
        SysDemandStage stage = submittedStage(new BigDecimal("100.00"));
        SysDemand d = demand("in_progress");
        when(stageMapper.selectByStageId(STAGE_ID)).thenReturn(stage);
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(d);
        when(stageMapper.updateReview(eq(STAGE_ID), eq(SysDemandStage.STAGE_STATUS_REJECTED),
                eq(REJECT_REASON), eq(0), eq(String.valueOf(BUYER_ID)))).thenReturn(1);

        service.reviewStage(DEMAND_ID, STAGE_ID, BUYER_ID, false, REJECT_REASON);

        verify(notifyService, times(1)).notifyStageRejectedByBuyer(
                eq(d), eq(stage), eq(REJECT_REASON), any(Date.class), anyLong());
        // 未通过分支绝不触发完成通知
        verify(notifyService, never()).notifyDemandCompleted(any(SysDemand.class));
    }

    @Test
    @DisplayName("E4 末轮通过：updateFundState 置 COMPLETED 后触发完成通知（仅传 demand）")
    void reviewStageLastRoundApproved_notifiesBothCompleted()
    {
        SysDemandStage stage = submittedStage(new BigDecimal("100.00"));
        SysDemand demand = demand("in_progress");
        when(stageMapper.selectByStageId(STAGE_ID)).thenReturn(stage);
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(demand);
        when(stageMapper.updateReview(eq(STAGE_ID), eq(SysDemandStage.STAGE_STATUS_APPROVED),
                eq(null), eq(1), eq(String.valueOf(BUYER_ID)))).thenReturn(1);
        Map<String, Object> settle = new HashMap<>();
        settle.put("sellerAmount", new BigDecimal("95.00"));
        when(financeService.createPendingStageSettle(demand, stage)).thenReturn(settle);
        when(stageMapper.selectNextPending(DEMAND_ID, STAGE_ORDER)).thenReturn(null);

        service.reviewStage(DEMAND_ID, STAGE_ID, BUYER_ID, true, null);

        verify(demandMapper, times(1)).updateFundState(eq(DEMAND_ID),
                eq(TradeDemandService.DEMAND_STATUS_COMPLETED),
                eq(TradeDemandService.ESCROW_RELEASED),
                eq(TradeDemandService.DEPOSIT_REFUNDED), eq(null));
        verify(notifyService, times(1)).notifyStageSettleCreated(
                eq(DEMAND_ID), eq(DEMAND_TITLE), eq(STAGE_ORDER),
                eq(new BigDecimal("100.00")), any(BigDecimal.class), eq(CREATOR_ID));
        verify(notifyService, times(1)).notifyDemandCompleted(eq(demand));
        verify(notifyService, never()).notifyStageRejectedByBuyer(
                any(SysDemand.class), any(SysDemandStage.class), anyString(), any(Date.class), anyLong());
    }

    @Test
    @DisplayName("非末轮通过：仅激活下一轮，不触发完成通知")
    void reviewStageNotLastRoundApproved_doesNotNotifyCompleted()
    {
        SysDemandStage stage = submittedStage(new BigDecimal("100.00"));
        SysDemand d = demand("in_progress");
        SysDemandStage next = new SysDemandStage();
        next.setStageId(STAGE_ID + 1);
        next.setStageOrder(STAGE_ORDER + 1);
        next.setDurationDays(3);
        when(stageMapper.selectByStageId(STAGE_ID)).thenReturn(stage);
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(d);
        when(stageMapper.updateReview(eq(STAGE_ID), eq(SysDemandStage.STAGE_STATUS_APPROVED),
                eq(null), eq(1), eq(String.valueOf(BUYER_ID)))).thenReturn(1);
        Map<String, Object> settle = new HashMap<>();
        settle.put("sellerAmount", new BigDecimal("95.00"));
        when(financeService.createPendingStageSettle(d, stage)).thenReturn(settle);
        when(stageMapper.selectNextPending(DEMAND_ID, STAGE_ORDER)).thenReturn(next);

        service.reviewStage(DEMAND_ID, STAGE_ID, BUYER_ID, true, null);

        verify(stageMapper, times(1)).activateStage(eq(STAGE_ID + 1), any(Date.class),
                any(Date.class), eq(String.valueOf(BUYER_ID)));
        verify(notifyService, never()).notifyDemandCompleted(any(SysDemand.class));
    }

    @Test
    @DisplayName("AI 审核：pass 走 readyForReview 分支，参数携带 (demand, stage)")
    void aiReviewPass_invokesReadyForReview()
    {
        DemandStageAiReviewService aiService = new DemandStageAiReviewService();
        SysDemandMapper demandMapper2 = mock(SysDemandMapper.class);
        TradeNotifyService notify2 = mock(TradeNotifyService.class);
        ReflectionTestUtils.setField(aiService, "demandMapper", demandMapper2);
        ReflectionTestUtils.setField(aiService, "notifyService", notify2);
        SysDemand d = demand("in_progress");
        when(demandMapper2.selectDemandById(DEMAND_ID)).thenReturn(d);

        SysDemandStage stage = submittedStage(new BigDecimal("100.00"));

        ReflectionTestUtils.invokeMethod(aiService, "notifyAiOutcome", stage, true, 85, null);

        verify(notify2, times(1)).notifyStageReadyForReview(eq(d), eq(stage));
        verify(notify2, never()).notifyStageRejectedByAi(
                any(SysDemand.class), any(SysDemandStage.class), any(), anyString());
    }

    @Test
    @DisplayName("AI 审核：fail 走 rejectedByAi 分支，参数携带 (demand, stage, score, reason)")
    void aiReviewFail_invokesRejectedByAi()
    {
        DemandStageAiReviewService aiService = new DemandStageAiReviewService();
        SysDemandMapper demandMapper2 = mock(SysDemandMapper.class);
        TradeNotifyService notify2 = mock(TradeNotifyService.class);
        ReflectionTestUtils.setField(aiService, "demandMapper", demandMapper2);
        ReflectionTestUtils.setField(aiService, "notifyService", notify2);
        SysDemand d = demand("in_progress");
        when(demandMapper2.selectDemandById(DEMAND_ID)).thenReturn(d);

        SysDemandStage stage = submittedStage(new BigDecimal("100.00"));
        String aiReason = "AI审核未通过（45分，低于60分自动打回）";

        ReflectionTestUtils.invokeMethod(aiService, "notifyAiOutcome", stage, false, 45, aiReason);

        verify(notify2, times(1)).notifyStageRejectedByAi(eq(d), eq(stage), eq(45), eq(aiReason));
        verify(notify2, never()).notifyStageReadyForReview(
                any(SysDemand.class), any(SysDemandStage.class));
    }

    @Test
    @DisplayName("AI 审核通知：任务已被删除时静默跳过，不影响主流程也不抛错")
    void aiReviewNotify_demandMissing_silentlySkips()
    {
        DemandStageAiReviewService aiService = new DemandStageAiReviewService();
        SysDemandMapper demandMapper2 = mock(SysDemandMapper.class);
        TradeNotifyService notify2 = mock(TradeNotifyService.class);
        ReflectionTestUtils.setField(aiService, "demandMapper", demandMapper2);
        ReflectionTestUtils.setField(aiService, "notifyService", notify2);
        when(demandMapper2.selectDemandById(DEMAND_ID)).thenReturn(null);

        SysDemandStage stage = submittedStage(new BigDecimal("100.00"));
        ReflectionTestUtils.invokeMethod(aiService, "notifyAiOutcome", stage, true, 85, null);

        verify(notify2, never()).notifyStageReadyForReview(
                any(SysDemand.class), any(SysDemandStage.class));
        verify(notify2, never()).notifyStageRejectedByAi(
                any(SysDemand.class), any(SysDemandStage.class), any(), anyString());
    }

    @Test
    @DisplayName("E6 超时终止：失败通知携带托管退回与押金没收入账金额")
    void terminateRound_notifiesWithDepositAmounts()
    {
        SysDemand d = demand("in_progress");
        d.setEscrowAmount(new BigDecimal("3000.00"));
        d.setDepositAmount(new BigDecimal("500.00"));
        SysDemandStage active = submittedStage(new BigDecimal("100.00"));
        active.setDueAt(new Date(System.currentTimeMillis() - 60_000L));
        when(demandMapper.failDemandIfInProgress(eq(DEMAND_ID),
                eq(TradeDemandService.DEMAND_STATUS_FAILED),
                eq(TradeDemandService.ESCROW_REFUNDED_TO_PUBLISHER),
                eq(TradeDemandService.DEPOSIT_FORFEITED), anyString())).thenReturn(1);

        ReflectionTestUtils.invokeMethod(service, "terminateRound", d, active, new Date());

        verify(notifyService, times(1)).notifyDemandRoundFailed(eq(DEMAND_ID), eq(DEMAND_TITLE),
                eq(STAGE_ORDER), anyString(), eq(BUYER_ID), eq(CREATOR_ID),
                eq(new BigDecimal("3000.00")), eq(new BigDecimal("500.00")));
    }

    @Test
    @DisplayName("E6 并发防重：CAS 终止落败（changed=0）不重发失败通知")
    void terminateRound_lostRace_noNotify()
    {
        SysDemand d = demand("in_progress");
        d.setEscrowAmount(new BigDecimal("3000.00"));
        d.setDepositAmount(new BigDecimal("500.00"));
        SysDemandStage active = submittedStage(new BigDecimal("100.00"));
        when(demandMapper.failDemandIfInProgress(any(), any(), any(), any(), any())).thenReturn(0);

        ReflectionTestUtils.invokeMethod(service, "terminateRound", d, active, new Date());

        verify(notifyService, never()).notifyDemandRoundFailed(
                any(), any(), any(), any(), any(), any(), any(), any());
    }
}
