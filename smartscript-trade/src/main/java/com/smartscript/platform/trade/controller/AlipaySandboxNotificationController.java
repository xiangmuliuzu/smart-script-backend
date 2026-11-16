package com.smartscript.platform.trade.controller;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.trade.service.AlipaySandboxPaymentService;

/**
 * 支付宝沙箱异步通知的统一入口。
 *
 * <p>需求任务的托管/押金收款码已随「发布权收归平台、款项站外结算」下线，
 * 本入口只服务订单域收款码（SSSB）。存量 SD 前缀回调不再有我侧单据，直接回 failure
 * 让支付宝按重试策略收敛，不能误投给订单域当作订单处理。</p>
 */
@RestController
public class AlipaySandboxNotificationController {
    /** 已退役的需求任务收款码前缀（SDESC 托管 / SDDEP 押金）。 */
    private static final String RETIRED_DEMAND_TRADE_NO_PREFIX = "SD";

    private final AlipaySandboxPaymentService service;

    public AlipaySandboxNotificationController(AlipaySandboxPaymentService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/v1/trade/alipay/sandbox/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(@RequestParam Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        if (outTradeNo != null && outTradeNo.startsWith(RETIRED_DEMAND_TRADE_NO_PREFIX)) {
            return "failure";
        }
        try {
            boolean settled = service.processNotification(params);
            return settled ? "success" : "failure";
        } catch (RuntimeException e) {
            return "failure";
        }
    }
}
