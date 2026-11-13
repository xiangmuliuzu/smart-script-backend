package com.smartscript.platform.trade.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.service.DemandSandboxPaymentService;
import com.smartscript.platform.trade.service.TradeDemandService;
import com.smartscript.platform.user.service.ChatService;

/**
 * 任务详情的游客口径回归：底部导航把「任务广场」做成公开板块后，详情接口第一次暴露给未登录调用方，
 * 必须同时满足「可见集合与广场列表一致」和「敏感字段不外泄」。
 */
class DemandDetailGuestViewTest
{
    private static final Long DEMAND_ID = 777L;

    private AppTradeDemandController controller(TradeDemandService service, boolean authenticated)
    {
        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.currentIdentity()).thenReturn(authenticated
                ? IdentityContext.authenticated(9001L, "personal", "00", List.of(), List.of(),
                        IdentityContext.REAL_NAME_NOT_SUBMITTED, true)
                : IdentityContext.guest());
        return new AppTradeDemandController(service, mock(DemandSandboxPaymentService.class), identityProvider,
                mock(ChatService.class));
    }

    private SysDemand demand(String status, String escrowStatus)
    {
        SysDemand d = new SysDemand();
        d.setDemandId(DEMAND_ID);
        d.setDemandNo("DM777");
        d.setClientId(9001L);
        d.setTitle("需要 30 集分镜");
        d.setGenreId(3);
        d.setBudget(new BigDecimal("6000"));
        d.setDeadline(new Date());
        d.setRequirement("每集 3 分钟");
        d.setContactInfo("13800000000");
        d.setTotalEpisodes(30);
        d.setStatus(status);
        d.setTaskType("storyboard");
        d.setSourceScriptUrl("https://files.invalid/private-script.pdf");
        d.setSourceScriptName("原剧本.pdf");
        d.setClientName("某影视公司");
        d.setEscrowAmount(new BigDecimal("6000"));
        d.setEscrowStatus(escrowStatus);
        d.setDepositAmount(new BigDecimal("600"));
        d.setDepositStatus("paid");
        return d;
    }

    @Test
    @SuppressWarnings("unchecked")
    void guestSeesOnlySquareVisibleTaskWithPublicFields()
    {
        TradeDemandService service = mock(TradeDemandService.class);
        when(service.selectDemandById(DEMAND_ID))
                .thenReturn(demand(TradeDemandService.DEMAND_STATUS_OPEN, TradeDemandService.ESCROW_ESCROWED));

        AppApiResponse<?> response = controller(service, false).detail(DEMAND_ID);
        assertEquals(200, response.getCode());
        Map<String, Object> view = (Map<String, Object>) response.getData();
        assertEquals("需要 30 集分镜", view.get("title"));
        assertEquals(TradeDemandService.ESCROW_ESCROWED, view.get("escrowStatus"));
        // 预算按接口约定以分返回
        assertEquals(0, new BigDecimal("600000").compareTo((BigDecimal) view.get("budget")));

        for (String sensitive : new String[] { "contactInfo", "clientId", "sourceScriptUrl",
                "escrowAmount", "depositAmount", "depositStatus", "acceptedCreatorId", "acceptedCreatorName" })
        {
            assertFalse(view.containsKey(sensitive), "游客响应不应包含敏感字段: " + sensitive);
        }
    }

    @Test
    void guestCannotSeeTaskOutsideSquareVisibility()
    {
        TradeDemandService service = mock(TradeDemandService.class);
        // 已接单/未托管/已关闭都不在广场可见集合内，游客一律按「不存在」处理（403 会泄露单号是否存在）。
        for (SysDemand d : new SysDemand[] {
                demand("in_progress", TradeDemandService.ESCROW_ESCROWED),
                demand(TradeDemandService.DEMAND_STATUS_OPEN, "pending_payment"),
                demand("closed", TradeDemandService.ESCROW_ESCROWED) })
        {
            when(service.selectDemandById(DEMAND_ID)).thenReturn(d);
            AppApiResponse<?> response = controller(service, false).detail(DEMAND_ID);
            assertEquals(404, response.getCode(),
                    "status=" + d.getStatus() + " escrow=" + d.getEscrowStatus() + " 对游客应不可见");
        }
    }

    @Test
    void authenticatedUserStillGetsFullEntity()
    {
        TradeDemandService service = mock(TradeDemandService.class);
        SysDemand stored = demand("in_progress", TradeDemandService.ESCROW_ESCROWED);
        when(service.selectDemandById(DEMAND_ID)).thenReturn(stored);

        AppApiResponse<?> response = controller(service, true).detail(DEMAND_ID);
        assertEquals(200, response.getCode());
        SysDemand data = assertInstanceOf(SysDemand.class, response.getData());
        assertEquals("13800000000", data.getContactInfo());
        assertEquals(600000, data.getBudget().intValue());
    }
}
