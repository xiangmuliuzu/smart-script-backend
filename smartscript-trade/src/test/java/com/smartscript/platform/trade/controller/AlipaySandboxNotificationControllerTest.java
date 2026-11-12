package com.smartscript.platform.trade.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.trade.service.AlipaySandboxPaymentService;
import com.smartscript.platform.trade.service.DemandSandboxPaymentService;

/** 沙箱异步通知分流：需求收款码（SDESC/SDDEP）必须交给需求域结算，订单码（SSSB）保持原路径。 */
class AlipaySandboxNotificationControllerTest
{
    final AlipaySandboxPaymentService orderService = mock(AlipaySandboxPaymentService.class);
    final DemandSandboxPaymentService demandService = mock(DemandSandboxPaymentService.class);
    final AlipaySandboxNotificationController controller =
            new AlipaySandboxNotificationController(orderService, demandService);

    private Map<String, String> params(String outTradeNo)
    {
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", outTradeNo);
        return params;
    }

    @Test
    void escrowCodeIsRoutedToTheDemandService()
    {
        when(demandService.processNotification(anyMap())).thenReturn(true);

        assertEquals("success", controller.notify(params("SDESC0001")));

        verify(demandService).processNotification(anyMap());
        verify(orderService, never()).processNotification(anyMap());
    }

    @Test
    void depositCodeIsRoutedToTheDemandService()
    {
        when(demandService.processNotification(anyMap())).thenReturn(true);

        assertEquals("success", controller.notify(params("SDDEP0001")));

        verify(demandService).processNotification(anyMap());
        verify(orderService, never()).processNotification(anyMap());
    }

    @Test
    void orderCodeStillGoesToTheOrderService()
    {
        when(orderService.processNotification(anyMap())).thenReturn(true);

        assertEquals("success", controller.notify(params("SSSB0001")));

        verify(orderService).processNotification(anyMap());
        verify(demandService, never()).processNotification(anyMap());
    }

    @Test
    void missingTradeNoFallsBackToTheOrderServiceAndFailureIsReportedBack()
    {
        Map<String, String> params = new HashMap<>();
        when(orderService.processNotification(anyMap())).thenReturn(false);

        assertEquals("failure", controller.notify(params));

        verify(orderService).processNotification(anyMap());
    }

    @Test
    void demandServiceBlowUpIsReportedAsFailureSoAlipayRetries()
    {
        when(demandService.processNotification(anyMap())).thenThrow(new IllegalStateException("沙箱未配置"));

        assertEquals("failure", controller.notify(params("SDESC0002")));
    }
}
