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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AI 生成字数上限配置接口测试（AiQuotaController limit-config）
 * 验证：
 * 1. GET 返回当前三个上限
 * 2. PUT 合法值 → 调 service 更新
 * 3. PUT 缺字段 / 负数 / 非整数 → 400，不调 service
 */
@ExtendWith(MockitoExtension.class)
class AiQuotaLimitConfigTest {

    @Mock
    private AiQuotaService aiQuotaService;

    @InjectMocks
    private AiQuotaController controller;

    @Test
    void getLimitConfig_返回三个上限() {
        Map<String, Object> data = new HashMap<>();
        data.put("write", 2000);
        data.put("polish", 2000);
        data.put("outline", 1000);
        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("code", 200);
        serviceResult.put("msg", "操作成功");
        serviceResult.put("data", data);
        when(aiQuotaService.getGenLimitConfig()).thenReturn(serviceResult);

        Map<String, Object> r = controller.getGenLimitConfig();

        assertEquals(200, r.get("code"));
        assertEquals(2000, ((Map<String, Object>) r.get("data")).get("write"));
        assertEquals(1000, ((Map<String, Object>) r.get("data")).get("outline"));
    }

    @Test
    void putLimitConfig_合法值_调service更新() {
        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("code", 200);
        serviceResult.put("msg", "生成字数上限已更新");
        when(aiQuotaService.updateGenLimitConfig(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(serviceResult);

        Map<String, Object> body = new HashMap<>();
        body.put("write", 3000);
        body.put("polish", 2500);
        body.put("outline", 1500);
        Map<String, Object> r = controller.updateGenLimitConfig(body);

        assertEquals(200, r.get("code"));
        verify(aiQuotaService).updateGenLimitConfig(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void putLimitConfig_缺字段_返回400不调service() {
        Map<String, Object> body = new HashMap<>();
        body.put("write", 2000);
        body.put("polish", 2000);

        Map<String, Object> r = controller.updateGenLimitConfig(body);

        assertEquals(400, r.get("code"));
        verifyNoInteractions(aiQuotaService);
    }

    @Test
    void putLimitConfig_负数_返回400不调service() {
        Map<String, Object> body = new HashMap<>();
        body.put("write", -1);
        body.put("polish", 2000);
        body.put("outline", 1000);

        Map<String, Object> r = controller.updateGenLimitConfig(body);

        assertEquals(400, r.get("code"));
        verifyNoInteractions(aiQuotaService);
    }

    @Test
    void putLimitConfig_非整数_返回400不调service() {
        Map<String, Object> body = new HashMap<>();
        body.put("write", "abc");
        body.put("polish", 2000);
        body.put("outline", 1000);

        Map<String, Object> r = controller.updateGenLimitConfig(body);

        assertEquals(400, r.get("code"));
        verifyNoInteractions(aiQuotaService);
    }
}
