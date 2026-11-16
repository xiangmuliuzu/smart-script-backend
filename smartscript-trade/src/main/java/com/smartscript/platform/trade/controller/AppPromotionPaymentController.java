package com.smartscript.platform.trade.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.trade.service.AlipaySandboxPromotionPaymentService;

/**
 * App 作品推广 · 支付宝沙箱支付
 *
 * POST /api/v1/trade/promotion/{applyId}/pay    创建预下单，返回 qrCode
 * POST /api/v1/trade/promotion/{applyId}/confirm 主动查单确认
 *
 * 鉴权：需 App Access Token，买家身份取自 IdentityProvider。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/trade/promotion")
public class AppPromotionPaymentController
{
    @Autowired
    private AlipaySandboxPromotionPaymentService paymentService;

    @Autowired
    private IdentityProvider identityProvider;

    @PostMapping("/{applyId}/pay")
    public AppApiResponse<Map<String, Object>> pay(@PathVariable Long applyId)
    {
        try
        {
            return AppApiResponse.ok(paymentService.create(applyId, identityProvider.currentUserId()));
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

    @PostMapping("/{applyId}/confirm")
    public AppApiResponse<Map<String, Object>> confirm(@PathVariable Long applyId)
    {
        try
        {
            return AppApiResponse.ok(paymentService.confirm(applyId, identityProvider.currentUserId()));
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
