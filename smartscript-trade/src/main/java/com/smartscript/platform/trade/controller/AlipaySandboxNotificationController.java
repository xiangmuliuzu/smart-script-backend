package com.smartscript.platform.trade.controller;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.trade.service.AlipaySandboxPaymentService;

@RestController
public class AlipaySandboxNotificationController {
    private final AlipaySandboxPaymentService service;
    public AlipaySandboxNotificationController(AlipaySandboxPaymentService service) { this.service = service; }
    @PostMapping(value = "/api/v1/trade/alipay/sandbox/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(@RequestParam Map<String, String> params) {
        try { return service.processNotification(params) ? "success" : "failure"; }
        catch (RuntimeException e) { return "failure"; }
    }
}
