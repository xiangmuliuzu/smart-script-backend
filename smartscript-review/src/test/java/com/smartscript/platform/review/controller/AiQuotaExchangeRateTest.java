package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.service.AiQuotaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AI 次数兑换比例配置接口测试（AiQuotaController）
 * 验证：
 * 1. GET 返回当前比例
 * 2. PUT 合法比例 → 调 service 更新
 * 3. PUT 缺 rate / 非整数 → 400，不调 service
 */
@ExtendWith(MockitoExtension.class)
class AiQuotaExchangeRateTest {

    @Mock
    private AiQuotaService aiQuotaService;

    @InjectMocks
    private AiQuotaController controller;

    @Test
    void getExchangeRate_返回当前比例() {
        when(aiQuotaService.getExchangeRate()).thenReturn(10);

        Map<String, Object> r = controller.exchangeRate();

        assertEquals(200, r.get("code"));
        assertEquals(10, r.get("data"));
    }

    @Test
    void putExchangeRate_合法比例_调service更新() {
        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("code", 200);
        serviceResult.put("msg", "兑换比例已更新为 5 积分=1次");
        serviceResult.put("rate", 5);
        when(aiQuotaService.updateExchangeRate(5)).thenReturn(serviceResult);

        Map<String, Object> body = new HashMap<>();
        body.put("rate", 5);
        Map<String, Object> r = controller.updateExchangeRate(body);

        assertEquals(200, r.get("code"));
        verify(aiQuotaService).updateExchangeRate(5);
    }

    @Test
    void putExchangeRate_缺rate_返回400不调service() {
        Map<String, Object> r = controller.updateExchangeRate(new HashMap<>());

        assertEquals(400, r.get("code"));
        verifyNoInteractions(aiQuotaService);
    }

    @Test
    void putExchangeRate_非整数_返回400不调service() {
        Map<String, Object> body = new HashMap<>();
        body.put("rate", "abc");

        Map<String, Object> r = controller.updateExchangeRate(body);

        assertEquals(400, r.get("code"));
        verifyNoInteractions(aiQuotaService);
    }

    @Test
    void putExchangeRate_service拒绝非法比例时透传() {
        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("code", 400);
        serviceResult.put("msg", "兑换比例必须 >= 1");
        when(aiQuotaService.updateExchangeRate(0)).thenReturn(serviceResult);

        Map<String, Object> body = new HashMap<>();
        body.put("rate", 0);
        Map<String, Object> r = controller.updateExchangeRate(body);

        assertEquals(400, r.get("code"));
        assertEquals("兑换比例必须 >= 1", r.get("msg"));
    }
}
