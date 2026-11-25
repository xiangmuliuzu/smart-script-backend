package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
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
 * 交付作品的用户侧可见闸（selectDeliverableWorkForViewer / selectStagesForViewer）。
 *
 * 验收链路已收归运营，正文只在 trade 域下发，所以闸必须区分三种人：
 *   - 接单创作者：看自己交付的轮次不受整单状态限制；
 *   - 需求方：只有整单完成（末轮验收通过）后能看末轮最终稿，中途轮次与未通过时一律拒绝；
 *   - 其他登录用户：无权查看，且轮次列表拿不到交付物与 AI 结果。
 */
class DemandFinalWorkAccessTest
{
    private static final long DEMAND_ID = 77L;
    private static final long BUYER_ID = 9091L;
    private static final long CREATOR_ID = 5150L;
    private static final long STRANGER_ID = 6001L;
    private static final long MID_STAGE_ID = 80L;
    private static final long FINAL_STAGE_ID = 81L;
    private static final long WORK_ID = 92L;

    private SysDemandMapper demandMapper;
    private SysDemandStageMapper stageMapper;
    private TradeDemandService service;

    @BeforeEach
    void setUp()
    {
        demandMapper = mock(SysDemandMapper.class);
        stageMapper = mock(SysDemandStageMapper.class);
        service = new TradeDemandService();
        ReflectionTestUtils.setField(service, "demandMapper", demandMapper);
        ReflectionTestUtils.setField(service, "stageMapper", stageMapper);
    }

    private SysDemand demand(String status)
    {
        SysDemand d = new SysDemand();
        d.setDemandId(DEMAND_ID);
        d.setClientId(BUYER_ID);
        d.setAcceptedCreatorId(CREATOR_ID);
        d.setStatus(status);
        return d;
    }

    private SysDemandStage stage(Long stageId, int order, String status)
    {
        SysDemandStage s = new SysDemandStage();
        s.setStageId(stageId);
        s.setDemandId(DEMAND_ID);
        s.setStageOrder(order);
        s.setEpisodeTo(order * 5);
        s.setStatus(status);
        s.setWorkId(WORK_ID);
        s.setWorkTitle("第一轮稿");
        s.setDeliverableDesc("本轮交付说明");
        s.setDeliverableUrl("http://files.example.com/part1.zip");
        s.setAiScore(88);
        s.setAiResult("结构完整");
        return s;
    }

    /** 两轮：中间轮（第1轮，10集中前5集）+ 末轮（第2轮，全10集）。 */
    private List<SysDemandStage> rounds(String finalStatus)
    {
        List<SysDemandStage> list = new ArrayList<>();
        list.add(stage(MID_STAGE_ID, 1, SysDemandStage.STAGE_STATUS_APPROVED));
        list.add(stage(FINAL_STAGE_ID, 2, finalStatus));
        return list;
    }

    /** 走通到底的交付作品读取桩（正文与截断由 DemandDeliverableWorkVisibilityTest 覆盖，这里只测闸）。 */
    private void stubReadableWork()
    {
        when(stageMapper.selectByStageId(anyLong())).thenAnswer(inv -> {
            Long stageId = inv.getArgument(0);
            return stage(stageId, stageId.equals(FINAL_STAGE_ID) ? 2 : 1, SysDemandStage.STAGE_STATUS_APPROVED);
        });
        Map<String, Object> work = new HashMap<>();
        work.put("workId", WORK_ID);
        work.put("title", "最终剧本");
        when(stageMapper.selectDeliveredWorkBrief(WORK_ID)).thenReturn(work);
        when(stageMapper.selectChaptersForReview(anyLong(), any())).thenReturn(new ArrayList<>());
    }

    @Test
    @DisplayName("整单完成后，需求方读取末轮最终稿")
    void buyerReadsFinalRoundAfterCompletion()
    {
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_COMPLETED));
        when(stageMapper.selectByDemandId(DEMAND_ID))
                .thenReturn(rounds(SysDemandStage.STAGE_STATUS_APPROVED));
        stubReadableWork();

        Map<String, Object> result =
                service.selectDeliverableWorkForViewer(DEMAND_ID, FINAL_STAGE_ID, BUYER_ID);

        assertEquals("最终剧本", result.get("title"));
        assertEquals(2, result.get("stageOrder"));
    }

    @Test
    @DisplayName("任务尚未完成时，需求方读不到任何交付稿")
    void buyerBlockedBeforeCompletion()
    {
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_IN_PROGRESS));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.selectDeliverableWorkForViewer(DEMAND_ID, MID_STAGE_ID, BUYER_ID));
        assertTrue(e.getMessage().contains("尚未完成验收"), e.getMessage());
    }

    @Test
    @DisplayName("完成后需求方也只能看末轮，中途过程稿不外泄")
    void buyerBlockedOnIntermediateRound()
    {
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_COMPLETED));
        when(stageMapper.selectByDemandId(DEMAND_ID))
                .thenReturn(rounds(SysDemandStage.STAGE_STATUS_APPROVED));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.selectDeliverableWorkForViewer(DEMAND_ID, MID_STAGE_ID, BUYER_ID));
        assertTrue(e.getMessage().contains("最后一轮"), e.getMessage());
    }

    @Test
    @DisplayName("末轮尚未通过验收时拒绝，即使整单状态已是 completed")
    void buyerBlockedWhenFinalRoundNotApproved()
    {
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_COMPLETED));
        when(stageMapper.selectByDemandId(DEMAND_ID))
                .thenReturn(rounds(SysDemandStage.STAGE_STATUS_SUBMITTED));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.selectDeliverableWorkForViewer(DEMAND_ID, FINAL_STAGE_ID, BUYER_ID));
        assertTrue(e.getMessage().contains("尚未验收通过"), e.getMessage());
    }

    @Test
    @DisplayName("接单创作者不受整单状态限制，可回看自己交付的轮次")
    void creatorReadsOwnDeliveredRound()
    {
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_IN_PROGRESS));
        stubReadableWork();

        Map<String, Object> result =
                service.selectDeliverableWorkForViewer(DEMAND_ID, MID_STAGE_ID, CREATOR_ID);

        assertEquals("最终剧本", result.get("title"));
    }

    @Test
    @DisplayName("非当事人无权查看交付作品")
    void strangerIsRejected()
    {
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_COMPLETED));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.selectDeliverableWorkForViewer(DEMAND_ID, FINAL_STAGE_ID, STRANGER_ID));
        assertTrue(e.getMessage().contains("无权查看"), e.getMessage());
    }

    @Test
    @DisplayName("未登录不放到交付作品读取口")
    void anonymousIsRejected()
    {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.selectDeliverableWorkForViewer(DEMAND_ID, FINAL_STAGE_ID, null));
        assertTrue(e.getMessage().contains("请先登录"), e.getMessage());
    }

    @Test
    @DisplayName("广场上的单：非参与者看得到轮次排期，但拿不到交付物与 AI 结果")
    void strangerSeesScheduleWithoutDeliverables()
    {
        SysDemand open = demand(TradeDemandService.DEMAND_STATUS_OPEN);
        open.setDeadline(new java.util.Date(System.currentTimeMillis() + 86400000L));
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(open);
        when(stageMapper.selectByDemandId(DEMAND_ID)).thenReturn(rounds(SysDemandStage.STAGE_STATUS_PENDING));

        List<SysDemandStage> list = service.selectStagesForViewer(DEMAND_ID, STRANGER_ID);

        assertEquals(2, list.size());
        SysDemandStage first = list.get(0);
        assertEquals(1, first.getStageOrder());
        assertEquals(5, first.getEpisodeTo());
        assertNull(first.getWorkId());
        assertNull(first.getWorkTitle());
        assertNull(first.getDeliverableDesc());
        assertNull(first.getDeliverableUrl());
        assertNull(first.getAiScore());
        assertNull(first.getAiResult());
    }

    @Test
    @DisplayName("需求方与接单创作者看到完整轮次字段")
    void participantsSeeAllFields()
    {
        when(demandMapper.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_IN_PROGRESS));
        when(stageMapper.selectByDemandId(DEMAND_ID)).thenReturn(rounds(SysDemandStage.STAGE_STATUS_PENDING));

        for (Long viewer : new Long[] { BUYER_ID, CREATOR_ID })
        {
            List<SysDemandStage> list = service.selectStagesForViewer(DEMAND_ID, viewer);
            assertEquals("本轮交付说明", list.get(0).getDeliverableDesc(), "viewer=" + viewer);
            assertEquals(88, list.get(0).getAiScore(), "viewer=" + viewer);
        }
    }

    @Test
    @DisplayName("审核态与已过征集截止的单：非参与者连轮次排期都读不到")
    void unauditedOrExpiredDemandIsNotReadable()
    {
        SysDemand expiredOpen = demand(TradeDemandService.DEMAND_STATUS_OPEN);
        expiredOpen.setDeadline(new java.util.Date(System.currentTimeMillis() - 86400000L));
        for (SysDemand off : new SysDemand[] {
                demand(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW),
                demand(TradeDemandService.DEMAND_STATUS_REJECTED), expiredOpen })
        {
            when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(off);
            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> service.selectStagesForViewer(DEMAND_ID, STRANGER_ID), "status=" + off.getStatus());
            assertTrue(e.getMessage().contains("任务不存在"), "status=" + off.getStatus() + ": " + e.getMessage());
        }
    }

    @Test
    @DisplayName("收尾状态的单：非参与者仍能读排期，交付材料照旧裁剪（与详情可见性同口径）")
    void strangerSeesScheduleOfFinishedDemandWithoutDeliverables()
    {
        for (String status : new String[] { TradeDemandService.DEMAND_STATUS_IN_PROGRESS,
                TradeDemandService.DEMAND_STATUS_COMPLETED, TradeDemandService.DEMAND_STATUS_CLOSED })
        {
            when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(demand(status));
            when(stageMapper.selectByDemandId(DEMAND_ID)).thenReturn(rounds(SysDemandStage.STAGE_STATUS_APPROVED));

            List<SysDemandStage> list = service.selectStagesForViewer(DEMAND_ID, STRANGER_ID);

            assertEquals(2, list.size(), "status=" + status);
            assertEquals(5, list.get(0).getEpisodeTo(), "status=" + status);
            assertNull(list.get(0).getDeliverableUrl(), "status=" + status);
            assertNull(list.get(0).getWorkId(), "status=" + status);
        }
    }

    @Test
    @DisplayName("已退出广场的单：参与者仍可读回排期与交付物")
    void participantsStillReadClosedDemand()
    {
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(demand(TradeDemandService.DEMAND_STATUS_CLOSED));
        when(stageMapper.selectByDemandId(DEMAND_ID)).thenReturn(rounds(SysDemandStage.STAGE_STATUS_PENDING));

        List<SysDemandStage> list = service.selectStagesForViewer(DEMAND_ID, BUYER_ID);

        assertEquals("本轮交付说明", list.get(0).getDeliverableDesc());
    }
}
