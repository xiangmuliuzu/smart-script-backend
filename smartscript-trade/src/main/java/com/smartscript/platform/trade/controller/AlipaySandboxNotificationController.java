package com.smartscript.platform.trade.controller;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.trade.service.AlipaySandboxPaymentService;
import com.smartscript.platform.trade.service.DemandSandboxPaymentService;

/**
 * 支付宝沙箱异步通知的统一入口。
 *
 * <p>托管/押金收款码与订单收款码共用同一个 NOTIFY_URL 配置，因此必须在这里按商户单号前缀分流，
 * 否则需求任务的回调会被订单域当作查无此单而丢弃：钱已到账，任务却永不曝光或永不被接单。</p>
 */
@RestController
public class AlipaySandboxNotificationController {
    /** 需求任务收款码的商户单号前缀（SDESC 托管 / SDDEP 押金），订单码为 SSSB。 */
    private static final String DEMAND_TRADE_NO_PREFIX = "SD";

    private final AlipaySandboxPaymentService service;
    private final DemandSandboxPaymentService demandService;

    public AlipaySandboxNotificationController(AlipaySandboxPaymentService service,
            DemandSandboxPaymentService demandService) {
        this.service = service;
        this.demandService = demandService;
    }

    @PostMapping(value = "/api/v1/trade/alipay/sandbox/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(@RequestParam Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        boolean isDemand = outTradeNo != null && outTradeNo.startsWith(DEMAND_TRADE_NO_PREFIX);
        try {
            boolean settled = isDemand ? demandService.processNotification(params) : service.processNotification(params);
            return settled ? "success" : "failure";
        } catch (RuntimeException e) {
            return "failure";
        }
    }
}
