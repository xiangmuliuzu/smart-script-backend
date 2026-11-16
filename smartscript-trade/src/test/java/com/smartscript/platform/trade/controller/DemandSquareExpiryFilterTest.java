package com.smartscript.platform.trade.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.service.TradeDemandService;
import com.smartscript.platform.user.service.ChatService;

/**
 * 任务广场的征集截止口径回归：过了 deadline 的 open 单不再对外曝光，
 * 但「我的任务」仍要能看见自己接下的单——交付与验收并不因为征集截止而消失。
 *
 * <p>这些单在库里仍保持 status=open（发布权收归运营后由管理端编辑或关闭），
 * 过滤只发生在广场的查询条件上，因此本测试锁定「广场加了条件、个人分支没加条件」。</p>
 */
class DemandSquareExpiryFilterTest
{
    private SysDemand runList(TradeDemandService service, String mine, boolean authenticated)
    {
        SysDemand row = new SysDemand();
        row.setDemandId(801L);
        row.setStatus(TradeDemandService.DEMAND_STATUS_OPEN);
        row.setBudget(new BigDecimal("1000"));
        row.setDeadline(new Date());
        when(service.countDemandList(any())).thenReturn(1L);
        when(service.selectDemandPage(any())).thenReturn(List.of(row));

        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.currentIdentity()).thenReturn(authenticated
                ? IdentityContext.authenticated(9001L, "personal", "00", List.of(), List.of(),
                        IdentityContext.REAL_NAME_NOT_SUBMITTED, true)
                : IdentityContext.guest());
        // mine 分支取的是 APP 域 principal（Long userId），走 SecurityContextHolder 而非 IdentityProvider
        SecurityContextHolder.clearContext();
        if (authenticated)
        {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(9001L, null, List.of()));
        }

        new AppTradeDemandController(service, identityProvider, mock(ChatService.class))
                .list(null, null, null, null, mine, "created", null, null, null, null, 1, 20);

        ArgumentCaptor<SysDemand> captor = ArgumentCaptor.forClass(SysDemand.class);
        verify(service).selectDemandPage(captor.capture());
        return captor.getValue();
    }

    @Test
    void 广场分支只取未过征集截止的open单()
    {
        SysDemand query = runList(mock(TradeDemandService.class), null, false);

        assertEquals(TradeDemandService.DEMAND_STATUS_OPEN, query.getStatus());
        assertEquals(Boolean.TRUE, query.getExcludeExpired(), "广场必须显式排除 deadline 已过的单");
    }

    @Test
    void 登录用户看到的广场与游客同口径()
    {
        SysDemand query = runList(mock(TradeDemandService.class), null, true);

        assertEquals(TradeDemandService.DEMAND_STATUS_OPEN, query.getStatus());
        assertEquals(Boolean.TRUE, query.getExcludeExpired());
    }

    @Test
    void 我的任务分支不排除过期单也不收窄状态()
    {
        for (String mine : List.of("claimed", "published"))
        {
            SysDemand query = runList(mock(TradeDemandService.class), mine, true);

            assertNull(query.getExcludeExpired(), mine + " 分支仍要能看到已过征集截止的单");
            assertNull(query.getStatus(), mine + " 分支不按状态收窄");
        }
    }

    /* ===================== 详情可见性与接单：同一口径收口 ===================== */

    private static SysDemand expiredOpen(Long publisher, Long claimer)
    {
        SysDemand demand = new SysDemand();
        demand.setDemandId(801L);
        demand.setStatus(TradeDemandService.DEMAND_STATUS_OPEN);
        demand.setBudget(new BigDecimal("1000"));
        demand.setDeadline(new Date(System.currentTimeMillis() - 86400_000L));
        demand.setClientId(publisher);
        demand.setAcceptedCreatorId(claimer);
        return demand;
    }

    private AppApiResponse<?> detail(SysDemand demand, boolean authenticated)
    {
        TradeDemandService service = mock(TradeDemandService.class);
        when(service.selectDemandById(801L)).thenReturn(demand);
        bind(authenticated);
        return new AppTradeDemandController(service, identity(authenticated), mock(ChatService.class)).detail(801L);
    }

    private IdentityProvider identity(boolean authenticated)
    {
        IdentityProvider identityProvider = mock(IdentityProvider.class);
        when(identityProvider.currentIdentity()).thenReturn(authenticated
                ? IdentityContext.authenticated(9001L, "personal", "00", List.of(), List.of(),
                        IdentityContext.REAL_NAME_NOT_SUBMITTED, true)
                : IdentityContext.guest());
        return identityProvider;
    }

    private void bind(boolean authenticated)
    {
        SecurityContextHolder.clearContext();
        if (authenticated)
        {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(9001L, null, List.of()));
        }
    }

    @Test
    void 过期单的详情对游客按不存在处理()
    {
        assertEquals(404, detail(expiredOpen(7777L, null), false).getCode());
    }

    @Test
    void 过期单的详情对其他登录用户也按不存在处理()
    {
        assertEquals(404, detail(expiredOpen(7777L, 8888L), true).getCode());
    }

    @Test
    void 过期单的详情仍对发布方与接单方开放()
    {
        assertEquals(200, detail(expiredOpen(9001L, null), true).getCode(), "我发布的单要能在我的任务里打开");
        assertEquals(200, detail(expiredOpen(7777L, 9001L), true).getCode(), "我接下的单交付不因征集截止而中断");
    }

    @Test
    void 未过期的open单详情口径不变()
    {
        SysDemand live = expiredOpen(7777L, null);
        live.setDeadline(new Date(System.currentTimeMillis() + 86400_000L));
        assertEquals(200, detail(live, false).getCode());
    }

    @Test
    void 过期单不再进入接单()
    {
        TradeDemandService service = mock(TradeDemandService.class);
        when(service.selectDemandById(801L)).thenReturn(expiredOpen(7777L, null));
        bind(true);

        AppApiResponse<SysDemand> resp = new AppTradeDemandController(service, identity(true), mock(ChatService.class))
                .claim(801L);

        assertEquals(403, resp.getCode());
        verify(service, never()).claimDemand(any(), any());
    }

    @Test
    void 未过期的open单仍可正常接单()
    {
        TradeDemandService service = mock(TradeDemandService.class);
        SysDemand live = expiredOpen(7777L, null);
        live.setDeadline(new Date(System.currentTimeMillis() + 86400_000L));
        when(service.selectDemandById(801L)).thenReturn(live);
        when(service.claimDemand(801L, 9001L)).thenReturn(live);
        bind(true);

        assertEquals(200, new AppTradeDemandController(service, identity(true), mock(ChatService.class))
                .claim(801L).getCode());
        verify(service).claimDemand(801L, 9001L);
    }
}
