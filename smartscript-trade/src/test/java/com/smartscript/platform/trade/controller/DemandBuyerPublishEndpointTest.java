package com.smartscript.platform.trade.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandStage;
import com.smartscript.platform.trade.service.TradeDemandService;
import com.smartscript.platform.user.service.ChatService;

/**
 * 用户侧发布/编辑接口的出入参装配回归（发布链路已回到需求方手上）。
 *
 * 锁三件容易在前后端接缝处出错的事：
 *   - 预算按分进、按元落库（APP/PC 全站以分交互，库里以元存）；
 *   - 轮次 amount 按元直传、days/hours 成对带过来，缺 days 时由 Service 归一为 0；
 *   - 建单/改单都走需求方专用服务（状态与归属由服务层收口），控制器不再自行决定状态；
 *   - 出参预算回到分，服务层的业务异常按 400 的可读文案返回。
 */
class DemandBuyerPublishEndpointTest
{
    private static final Long BUYER_ID = 9001L;
    private TradeDemandService service;
    private AppTradeDemandController controller;

    @BeforeEach
    void setUp()
    {
        service = mock(TradeDemandService.class);
        IdentityProvider identityProvider = mock(IdentityProvider.class);
        controller = new AppTradeDemandController(service, identityProvider, mock(ChatService.class));
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(BUYER_ID, null, List.of()));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    private static Map<String, Object> form()
    {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "需要 10 集短剧剧本");
        body.put("genreId", 3);
        body.put("budget", 1000000L);                     // 分
        body.put("deadline", "2099-12-31");
        body.put("requirement", "每集 3 分钟");
        body.put("contactInfo", "13800000000");
        body.put("totalEpisodes", 10);
        body.put("durationHours", 336);
        body.put("taskType", TradeDemandService.TASK_TYPE_SCRIPT);
        List<Map<String, Object>> stages = new ArrayList<>();
        Map<String, Object> first = new HashMap<>();
        first.put("stageOrder", 1);
        first.put("episodeTo", 5);
        first.put("amount", new BigDecimal("6000"));      // 元
        first.put("durationHours", 168);
        stages.add(first);
        body.put("stages", stages);
        return body;
    }

    @Test
    @DisplayName("发布：预算分→元、轮次按元装配，并交给需求方建单服务")
    void createBuildsDemandInYuanAndDelegatesToBuyerService()
    {
        SysDemand created = new SysDemand();
        created.setDemandId(88L);
        created.setBudget(new BigDecimal("10000"));
        created.setStatus(TradeDemandService.DEMAND_STATUS_PENDING_REVIEW);
        when(service.insertDemandByBuyer(any(SysDemand.class), anyList(), eq(BUYER_ID))).thenReturn(created);

        AppApiResponse<SysDemand> resp = controller.create(form());

        assertEquals(200, resp.getCode());
        ArgumentCaptor<SysDemand> demandCap = ArgumentCaptor.forClass(SysDemand.class);
        ArgumentCaptor<List<SysDemandStage>> stageCap = ArgumentCaptor.forClass(List.class);
        verify(service).insertDemandByBuyer(demandCap.capture(), stageCap.capture(), eq(BUYER_ID));

        SysDemand sent = demandCap.getValue();
        assertEquals(0, new BigDecimal("10000").compareTo(sent.getBudget()), "分必须换算成元再交给服务层");
        assertEquals(1000000, resp.getData().getBudget().intValue(), "出参预算回到分供 APP 展示");
        assertEquals("2099-12-31", new java.text.SimpleDateFormat("yyyy-MM-dd").format(sent.getDeadline()));
        assertEquals(TradeDemandService.TASK_TYPE_SCRIPT, sent.getTaskType());

        SysDemandStage stage = stageCap.getValue().get(0);
        assertEquals(1, stage.getStageOrder());
        assertEquals(5, stage.getEpisodeTo());
        assertEquals(0, new BigDecimal("6000").compareTo(stage.getAmount()), "轮次金额按元直传");
        assertEquals(168, stage.getDurationHours());
    }

    @Test
    @DisplayName("发布：总集数缺失直接拦在控制器，不落到服务层")
    void createRequiresTotalEpisodes()
    {
        Map<String, Object> body = form();
        body.remove("totalEpisodes");

        assertEquals(400, controller.create(body).getCode());
        verify(service, org.mockito.Mockito.never())
                .insertDemandByBuyer(any(), any(), any());
    }

    @Test
    @DisplayName("发布：截止日期格式错误返回可读文案")
    void createRejectsBadDeadline()
    {
        Map<String, Object> body = form();
        body.put("deadline", "2099/12/31");

        AppApiResponse<SysDemand> resp = controller.create(body);

        assertEquals(400, resp.getCode());
        assertEquals("截止日期格式不正确，请重新选择", resp.getMessage());
    }

    @Test
    @DisplayName("编辑：带 demandId 走需求方改单服务，服务层异常按 400 返回")
    void updateDelegatesAndMapsErrors()
    {
        SysDemand saved = new SysDemand();
        saved.setDemandId(88L);
        saved.setBudget(new BigDecimal("8000"));
        when(service.updateDemandByBuyer(any(SysDemand.class), any(), eq(BUYER_ID))).thenReturn(saved);

        AppApiResponse<SysDemand> resp = controller.update(88L, form());

        assertEquals(200, resp.getCode());
        assertEquals(800000, resp.getData().getBudget().intValue());

        when(service.updateDemandByBuyer(any(SysDemand.class), any(), eq(BUYER_ID)))
                .thenThrow(new RuntimeException("任务已通过审核，如需调整请联系平台客服"));
        AppApiResponse<SysDemand> blocked = controller.update(88L, form());
        assertEquals(400, blocked.getCode());
        assertEquals("任务已通过审核，如需调整请联系平台客服", blocked.getMessage());
    }

    @Test
    @DisplayName("源剧本候选取当前登录用户自己的作品")
    void sourceWorksUseCurrentIdentity()
    {
        Map<String, Object> row = new HashMap<>();
        row.put("workId", 123L);
        when(service.selectSourceWorks(BUYER_ID)).thenReturn(List.of(row));

        assertEquals(200, controller.sourceWorks().getCode());
        verify(service).selectSourceWorks(BUYER_ID);
    }

    @Test
    @DisplayName("交付作品读取走带身份闸的服务方法")
    void deliverableWorkGoesThroughViewerGate()
    {
        Map<String, Object> work = new HashMap<>();
        work.put("title", "最终剧本");
        when(service.selectDeliverableWorkForViewer(88L, 81L, BUYER_ID)).thenReturn(work);

        assertEquals(200, controller.deliverableWork(88L, 81L).getCode());
        verify(service).selectDeliverableWorkForViewer(88L, 81L, BUYER_ID);

        when(service.selectDeliverableWorkForViewer(88L, 81L, BUYER_ID))
                .thenThrow(new RuntimeException("任务尚未完成验收，暂不可查看交付作品"));
        AppApiResponse<Map<String, Object>> denied = controller.deliverableWork(88L, 81L);
        assertEquals(400, denied.getCode());
        assertEquals("任务尚未完成验收，暂不可查看交付作品", denied.getMessage());
    }

    @Test
    @DisplayName("轮次列表走 viewer 口径并把金额元→分")
    void stagesUseViewerScope()
    {
        SysDemandStage stage = new SysDemandStage();
        stage.setStageOrder(1);
        stage.setAmount(new BigDecimal("6000"));
        when(service.selectStagesForViewer(88L, BUYER_ID)).thenReturn(new ArrayList<>(List.of(stage)));

        AppApiResponse<List<SysDemandStage>> resp = controller.stages(88L);

        assertEquals(200, resp.getCode());
        assertEquals(600000, resp.getData().get(0).getAmount().intValue());
    }
}
