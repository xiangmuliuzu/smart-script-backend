package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.service.AiQuotaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 「1积分=N token」配置接口测试（AiQuotaController token-per-point）
 * 覆盖去次数化后取代字数上限的这条配置：
 * 1. GET 返回当前配置
 * 2. PUT 合法值 → 调 service 更新并透传
 * 3. PUT 缺字段 / 非整数 → 400，不调 service
 */
@ExtendWith(MockitoExtension.class)
class AiQuotaTokenPerPointTest {

    @Mock
    private AiQuotaService aiQuotaService;

    @InjectMocks
    private AiQuotaController controller;

    @Test
    void getTokenPerPoint_返回当前配置() {
        when(aiQuotaService.getTokenPerPoint()).thenReturn(1000);

        Map<String, Object> r = controller.tokenPerPoint();

        assertEquals(200, r.get("code"));
        assertEquals(1000, r.get("data"));
    }

    @Test
    void putTokenPerPoint_合法值_调service更新() {
        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("code", 200);
        serviceResult.put("msg", "已更新为 1 积分 = 1500 token");
        serviceResult.put("tokenPerPoint", 1500);
        when(aiQuotaService.updateTokenPerPoint(1500)).thenReturn(serviceResult);

        Map<String, Object> body = new HashMap<>();
        body.put("tokenPerPoint", 1500);
        Map<String, Object> r = controller.updateTokenPerPoint(body);

        assertEquals(200, r.get("code"));
        assertEquals(1500, r.get("data"));
        verify(aiQuotaService).updateTokenPerPoint(1500);
    }

    @Test
    void putTokenPerPoint_缺字段_返回400不调service() {
        Map<String, Object> r = controller.updateTokenPerPoint(new HashMap<>());

        assertEquals(400, r.get("code"));
        verifyNoInteractions(aiQuotaService);
    }

    @Test
    void putTokenPerPoint_非整数_返回400不调service() {
        Map<String, Object> body = new HashMap<>();
        body.put("tokenPerPoint", "abc");

        Map<String, Object> r = controller.updateTokenPerPoint(body);

        assertEquals(400, r.get("code"));
        verifyNoInteractions(aiQuotaService);
    }

    @Test
    void putTokenPerPoint_service拒绝时透传() {
        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("code", 400);
        serviceResult.put("msg", "token/积分 必须 >= 1");
        when(aiQuotaService.updateTokenPerPoint(anyInt())).thenReturn(serviceResult);

        Map<String, Object> body = new HashMap<>();
        body.put("tokenPerPoint", 0);
        Map<String, Object> r = controller.updateTokenPerPoint(body);

        assertEquals(400, r.get("code"));
        assertEquals("token/积分 必须 >= 1", r.get("msg"));
    }
}
