package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
 * 需求方自助发布 + 平台审核状态机单元测试。
 *
 * 口径必须锁死四件事：
 *   - 买方建单落 pending_review（不进广场），client_id/create_by 固定为提交人本人；
 *   - storyboard 的源剧本必须属于提交人（代客建单不校验，两条链路同一实现不同参数）；
 *   - 审核写回走 CAS（只认仍在待审核的单），通过后 open、驳回后 rejected 且意见落库；
 *   - 被驳回的单改后重新提交：状态回到 pending_review 并作废上一轮审核意见。
 */
class DemandPublishAuditTest
{
    private static final long BUYER_ID = 9091L;
    private static final long AUDITOR_ID = 7L;
    private static final long DEMAND_ID = 88L;

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

    private SysDemand demand(String status)
    {
        SysDemand d = new SysDemand();
        d.setDemandId(DEMAND_ID);
        d.setDemandNo("DM0001");
        d.setClientId(BUYER_ID);
        d.setTitle("十集短剧剧本");
        d.setBudget(new BigDecimal("10000"));
        d.setTotalEpisodes(10);
        d.setStatus(status);
        return d;
    }

    private Map<String, Object> workBrief(Long authorId)
    {
        Map<String, Object> work = new HashMap<>();
        work.put("workId", 123L);
        work.put("authorId", authorId);
        work.put("status", "off_shelf");
        return work;
    }

    @Test
    @DisplayName("买方建单落待审核，需求方与建单人固定为提交人本人")
    void buyerCreateStartsAsPendingReview()
    {
        SysDemand input = demand(null);
        input.setClientId(null);
        input.setCreateBy(null);
        when(demandMapper.insertDemand(any(SysDemand.class))).thenAnswer(inv -> {
            ((SysDemand) inv.getArgument(0)).setDemandId(DEMAND_ID);
            return 1;
        });
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(demand(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW));

        service.insertDemandByBuyer(input, null, BUYER_ID);

        assertEquals(BUYER_ID, input.getClientId());
        assertEquals(String.valueOf(BUYER_ID), input.getCreateBy());
        assertEquals(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW, input.getStatus());
        verify(notifyService).notifyDemandPublishSubmitted(any(SysDemand.class), anyLong(), eq(false));
    }

    @Test
    @DisplayName("未登录不能建单")
    void buyerCreateRequiresIdentity()
    {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.insertDemandByBuyer(demand(null), null, null));
        assertTrue(e.getMessage().contains("请先登录"), e.getMessage());
        verify(demandMapper, never()).insertDemand(any(SysDemand.class));
    }

    @Test
    @DisplayName("storyboard 源剧本必须是本人作品")
    void buyerSourceWorkMustBelongToBuyer()
    {
        SysDemand input = demand(null);
        input.setTaskType(TradeDemandService.TASK_TYPE_STORYBOARD);
        input.setSourceWorkId(123L);
        when(stageMapper.selectWorkBrief(123L)).thenReturn(workBrief(BUYER_ID + 1));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.insertDemandByBuyer(input, null, BUYER_ID));
        assertTrue(e.getMessage().contains("源剧本作品必须属于本人"), e.getMessage());
    }

    @Test
    @DisplayName("运营代客建单不校验源剧本归属（历史口径保持不变）")
    void adminCreateSkipsSourceOwnership()
    {
        SysDemand input = demand(null);
        input.setTaskType(TradeDemandService.TASK_TYPE_STORYBOARD);
        input.setSourceWorkId(123L);
        input.setClientId(AUDITOR_ID);
        input.setCreateBy("admin");
        when(stageMapper.selectWorkBrief(123L)).thenReturn(workBrief(BUYER_ID));
        when(demandMapper.insertDemand(any(SysDemand.class))).thenReturn(1);

        service.insertDemand(input, null);

        assertEquals(TradeDemandService.DEMAND_STATUS_OPEN, input.getStatus());
    }

    @Test
    @DisplayName("审核通过：CAS 到 open 并通知需求方")
    void approvePublishOpensSquare()
    {
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_OPEN));
        when(demandMapper.updateAuditCas(eq(DEMAND_ID), eq(TradeDemandService.DEMAND_STATUS_OPEN),
                any(), eq(AUDITOR_ID), anyString())).thenReturn(1);

        SysDemand result = service.approveDemandPublish(DEMAND_ID, AUDITOR_ID);

        assertEquals(TradeDemandService.DEMAND_STATUS_OPEN, result.getStatus());
        verify(notifyService).notifyDemandPublishAudited(any(SysDemand.class), eq(true), any(), anyLong());
    }

    @Test
    @DisplayName("审核驳回：落 rejected 并保留驳回意见")
    void rejectPublishStoresRemark()
    {
        SysDemand rejected = demand(TradeDemandService.DEMAND_STATUS_REJECTED);
        rejected.setAuditRemark("预算与集数不匹配");
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW))
                .thenReturn(rejected);
        when(demandMapper.updateAuditCas(eq(DEMAND_ID), eq(TradeDemandService.DEMAND_STATUS_REJECTED),
                eq("预算与集数不匹配"), eq(AUDITOR_ID), anyString())).thenReturn(1);

        SysDemand result = service.rejectDemandPublish(DEMAND_ID, AUDITOR_ID, "预算与集数不匹配");

        assertEquals(TradeDemandService.DEMAND_STATUS_REJECTED, result.getStatus());
        assertEquals("预算与集数不匹配", result.getAuditRemark());
    }

    @Test
    @DisplayName("驳回必须填原因")
    void rejectRequiresReason()
    {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.rejectDemandPublish(DEMAND_ID, AUDITOR_ID, "  "));
        assertTrue(e.getMessage().contains("请填写驳回原因"), e.getMessage());
        verify(demandMapper, never()).updateAuditCas(anyLong(), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("不在待审核列表的单不可审核")
    void onlyPendingReviewIsAuditable()
    {
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(demand(TradeDemandService.DEMAND_STATUS_OPEN));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.approveDemandPublish(DEMAND_ID, AUDITOR_ID));
        assertTrue(e.getMessage().contains("待审核"), e.getMessage());
    }

    @Test
    @DisplayName("CAS 未命中（并发重复审核）不再通知")
    void concurrentAuditIsRejected()
    {
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW));
        when(demandMapper.updateAuditCas(anyLong(), anyString(), any(), any(), anyString())).thenReturn(0);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.approveDemandPublish(DEMAND_ID, AUDITOR_ID));
        assertTrue(e.getMessage().contains("已被审核"), e.getMessage());
        verify(notifyService, never()).notifyDemandPublishAudited(any(), eq(true), any(), anyLong());
    }

    @Test
    @DisplayName("驳回后重新提交：回到待审核并作废上一轮审核意见")
    void rejectedResubmitBackToPendingReview()
    {
        SysDemand existing = demand(TradeDemandService.DEMAND_STATUS_REJECTED);
        existing.setTaskType(TradeDemandService.TASK_TYPE_SCRIPT);
        when(demandMapper.selectDemandForUpdate(DEMAND_ID)).thenReturn(existing);
        when(demandMapper.updateStatusCas(DEMAND_ID, TradeDemandService.DEMAND_STATUS_PENDING_REVIEW,
                String.valueOf(BUYER_ID), TradeDemandService.DEMAND_STATUS_REJECTED)).thenReturn(1);
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW));

        SysDemand form = demand(null);
        form.setTaskType(TradeDemandService.TASK_TYPE_SCRIPT);
        form.setBudget(new BigDecimal("8000"));
        service.updateDemandByBuyer(form, null, BUYER_ID);

        assertTrue(Boolean.TRUE.equals(form.getResetAudit()), "重新提交要作废上一轮审核意见");
        verify(demandMapper).updateStatusCas(DEMAND_ID, TradeDemandService.DEMAND_STATUS_PENDING_REVIEW,
                String.valueOf(BUYER_ID), TradeDemandService.DEMAND_STATUS_REJECTED);
        verify(notifyService).notifyDemandPublishSubmitted(any(SysDemand.class), anyLong(), eq(true));
    }

    @Test
    @DisplayName("只能修改自己提交的单")
    void cannotEditOthersDemand()
    {
        when(demandMapper.selectDemandForUpdate(DEMAND_ID)).thenReturn(demand(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.updateDemandByBuyer(demand(null), null, BUYER_ID + 1));
        assertTrue(e.getMessage().contains("自己提交"), e.getMessage());
    }

    @Test
    @DisplayName("已过审进广场的单不再允许用户侧编辑")
    void squareVisibleDemandIsLockedForBuyer()
    {
        when(demandMapper.selectDemandForUpdate(DEMAND_ID)).thenReturn(demand(TradeDemandService.DEMAND_STATUS_OPEN));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.updateDemandByBuyer(demand(null), null, BUYER_ID));
        assertTrue(e.getMessage().contains("已通过审核"), e.getMessage());
    }

    @Test
    @DisplayName("运营编辑仍只认 open 未接单，且不清空审核意见")
    void adminEditKeepsOpenGuard()
    {
        SysDemand existing = demand(TradeDemandService.DEMAND_STATUS_IN_PROGRESS);
        existing.setTaskType(TradeDemandService.TASK_TYPE_SCRIPT);
        when(demandMapper.selectDemandForUpdate(DEMAND_ID)).thenReturn(existing);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.updateDemand(demand(null), null, AUDITOR_ID));
        assertTrue(e.getMessage().contains("不可编辑"), e.getMessage());
    }

    @Test
    @DisplayName("待审核/被驳回的单不属于广场可见集合")
    void unauditedDemandsAreNotSquareVisible()
    {
        SysDemand pending = demand(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW);
        pending.setDeadline(new Date(System.currentTimeMillis() + 86400000L));
        SysDemand rejected = demand(TradeDemandService.DEMAND_STATUS_REJECTED);
        rejected.setDeadline(new Date(System.currentTimeMillis() + 86400000L));

        assertTrue(TradeDemandService.isSquareVisible(demand(TradeDemandService.DEMAND_STATUS_OPEN)));
        assertFalse(TradeDemandService.isSquareVisible(pending));
        assertFalse(TradeDemandService.isSquareVisible(rejected));
    }

    @Test
    @DisplayName("退出广场只含审核态与过截止：收尾状态仍对登录用户可读")
    void offSquareCoversAuditStatesOnly()
    {
        SysDemand expired = demand(TradeDemandService.DEMAND_STATUS_OPEN);
        expired.setDeadline(new Date(System.currentTimeMillis() - 86400000L));

        for (SysDemand off : new SysDemand[] {
                demand(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW),
                demand(TradeDemandService.DEMAND_STATUS_REJECTED), expired })
        {
            assertTrue(TradeDemandService.isOffSquare(off), off.getStatus() + " 应只留给当事人");
        }
        // 进行中/已完成/已关闭不在广场列表里，但详情与轮次排期不是审核队列内容，不该按「不存在」处理
        for (String status : List.of(TradeDemandService.DEMAND_STATUS_IN_PROGRESS,
                TradeDemandService.DEMAND_STATUS_COMPLETED, TradeDemandService.DEMAND_STATUS_CLOSED))
        {
            assertFalse(TradeDemandService.isOffSquare(demand(status)), status + " 仍应可被普通用户读到排期");
        }
    }
}
