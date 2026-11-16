package com.smartscript.platform.trade.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.trade.service.AlipaySandboxPaymentService;

/** 沙箱异步通知：订单码（SSSB）走订单域结算；已退役的需求收款码（SD*）不再投递给任何服务。 */
class AlipaySandboxNotificationControllerTest
{
    final AlipaySandboxPaymentService orderService = mock(AlipaySandboxPaymentService.class);
    final AlipaySandboxNotificationController controller =
            new AlipaySandboxNotificationController(orderService);

    private Map<String, String> params(String outTradeNo)
    {
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", outTradeNo);
        return params;
    }

    @Test
    void orderCodeGoesToTheOrderService()
    {
        when(orderService.processNotification(anyMap())).thenReturn(true);

        assertEquals("success", controller.notify(params("SSSB0001")));

        verify(orderService).processNotification(anyMap());
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
    void orderServiceBlowUpIsReportedAsFailureSoAlipayRetries()
    {
        when(orderService.processNotification(anyMap())).thenThrow(new IllegalStateException("沙箱未配置"));

        assertEquals("failure", controller.notify(params("SSSB0002")));
    }

    /** 托管/押金收款码已下线：SD 前缀回调绝不能被当成订单结算，直接回 failure 让支付宝收敛。 */
    @Test
    void retiredDemandCodeIsRejectedWithoutTouchingTheOrderService()
    {
        assertEquals("failure", controller.notify(params("SDESC0001")));
        assertEquals("failure", controller.notify(params("SDDEP0001")));

        verify(orderService, never()).processNotification(anyMap());
    }
}
