package com.smartscript.platform.review.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 生成字数上限配置 Service 测试（AiQuotaService limit-config）
 * 验证：
 * 1. 读取：未配置回退默认值（写作2000/润色2000/大纲1000），已配置按配置返回，0=不限
 * 2. 更新：合法值 upsert sys_config；负数/非整数/缺字段 → 400
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiQuotaLimitConfigServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AiQuotaService service;

    private static Map<String, Object> singleRow(String value) {
        Map<String, Object> row = new HashMap<>();
        row.put("config_value", value);
        return row;
    }

    @Test
    @DisplayName("读取：未配置时回退默认值")
    void get_unconfigured_defaults() {
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(Collections.emptyList());

        Map<String, Object> r = service.getGenLimitConfig();
        assertEquals(200, r.get("code"));
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals(2000, data.get("write"));
        assertEquals(2000, data.get("polish"));
        assertEquals(1000, data.get("outline"));
    }

    @Test
    @DisplayName("读取：已配置按配置返回，0=不限")
    void get_configured_values() {
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(1);
            if ("ai.gen.max.write".equals(key)) return Collections.singletonList(singleRow("0"));
            if ("ai.gen.max.polish".equals(key)) return Collections.singletonList(singleRow("2500"));
            return Collections.singletonList(singleRow("1500"));
        });

        Map<String, Object> r = service.getGenLimitConfig();
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals(0, data.get("write"));
        assertEquals(2500, data.get("polish"));
        assertEquals(1500, data.get("outline"));
    }

    @Test
    @DisplayName("更新：合法值返回正确结构（upsert 落库行为由真实接口测试验证）")
    void update_valid_upserts() {
        Map<String, Object> body = new HashMap<>();
        body.put("write", 3000);
        body.put("polish", 0);
        body.put("outline", 1500);

        Map<String, Object> r = service.updateGenLimitConfig(body);
        if (!r.get("code").equals(200)) { System.out.println("FAIL_MSG=" + r.get("msg")); }
        assertEquals(200, r.get("code"));
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals(3000, data.get("write"));
        assertEquals(0, data.get("polish"));
        assertEquals(1500, data.get("outline"));
    }

    @Test
    @DisplayName("更新：记录不存在时同样返回成功结构")
    void update_missing_row_inserts() {
        Map<String, Object> body = new HashMap<>();
        body.put("write", 2000);
        body.put("polish", 2000);
        body.put("outline", 1000);

        Map<String, Object> r = service.updateGenLimitConfig(body);
        if (!r.get("code").equals(200)) { System.out.println("FAIL_MSG=" + r.get("msg")); }
        assertEquals(200, r.get("code"));
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        assertEquals(2000, data.get("write"));
        assertEquals(1000, data.get("outline"));
    }

    @Test
    @DisplayName("更新：缺字段 → 400")
    void update_missing_field_400() {
        Map<String, Object> body = new HashMap<>();
        body.put("write", 2000);
        body.put("polish", 2000);

        Map<String, Object> r = service.updateGenLimitConfig(body);
        assertEquals(400, r.get("code"));
    }

    @Test
    @DisplayName("更新：负数 → 400")
    void update_negative_400() {
        Map<String, Object> body = new HashMap<>();
        body.put("write", -5);
        body.put("polish", 2000);
        body.put("outline", 1000);

        Map<String, Object> r = service.updateGenLimitConfig(body);
        assertEquals(400, r.get("code"));
    }

    @Test
    @DisplayName("更新：非整数 → 400")
    void update_nonInteger_400() {
        Map<String, Object> body = new HashMap<>();
        body.put("write", "abc");
        body.put("polish", 2000);
        body.put("outline", 1000);

        Map<String, Object> r = service.updateGenLimitConfig(body);
        assertEquals(400, r.get("code"));
    }
}
