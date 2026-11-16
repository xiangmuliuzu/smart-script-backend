package com.smartscript.platform.trade.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.trade.service.AlipaySandboxCopyrightPaymentService;

/**
 * App 版权确权 · 支付宝沙箱支付
 *
 * POST /api/v1/trade/copyright/{payOrderId}/pay
 * POST /api/v1/trade/copyright/{payOrderId}/confirm
 */
@RestController
@RequestMapping("/api/v1/trade/copyright")
public class AppCopyrightPaymentController
{
    @Autowired
    private AlipaySandboxCopyrightPaymentService paymentService;

    @Autowired
    private IdentityProvider identityProvider;

    @PostMapping("/{payOrderId}/pay")
    public AppApiResponse<Map<String, Object>> pay(@PathVariable Long payOrderId)
    {
        try
        {
            return AppApiResponse.ok(paymentService.create(payOrderId, identityProvider.currentUserId()));
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(404, e.getMessage());
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping("/{payOrderId}/confirm")
    public AppApiResponse<Map<String, Object>> confirm(@PathVariable Long payOrderId)
    {
        try
        {
            return AppApiResponse.ok(paymentService.confirm(payOrderId, identityProvider.currentUserId()));
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(404, e.getMessage());
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
    }
}
