package com.smartscript.platform.review.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「1积分=N token」配置 Service 测试（AiQuotaService token-per-point）
 * 覆盖去次数化后取代字数上限的这条配置：
 * 1. 读取：未配置/非法/异常 → 回退默认 1000；配置 >=1 按配置返回
 * 2. 更新：<1 → 400；存在则 UPDATE，不存在则 INSERT
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiQuotaTokenPerPointTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AiQuotaService service;

    private static Map<String, Object> row(String value) {
        Map<String, Object> r = new HashMap<>();
        r.put("config_value", value);
        return r;
    }

    @Test
    @DisplayName("读取：未配置时回退默认 1000")
    void get_unconfigured_defaults() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.emptyList());

        assertEquals(1000, service.getTokenPerPoint());
    }

    @Test
    @DisplayName("读取：已配置按配置返回")
    void get_configured_value() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.singletonList(row("500")));

        assertEquals(500, service.getTokenPerPoint());
    }

    @Test
    @DisplayName("读取：非法值 0 回退默认 1000")
    void get_zero_falls_back() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.singletonList(row("0")));

        assertEquals(1000, service.getTokenPerPoint());
    }

    @Test
    @DisplayName("读取：查询异常回退默认 1000")
    void get_exception_falls_back() {
        when(jdbcTemplate.queryForList(anyString())).thenThrow(new RuntimeException("db down"));

        assertEquals(1000, service.getTokenPerPoint());
    }

    @Test
    @DisplayName("更新：小于 1 → 400 且不落库")
    void update_below_one_400() {
        Map<String, Object> r = service.updateTokenPerPoint(0);

        assertEquals(400, r.get("code"));
        verify(jdbcTemplate, never()).update(anyString(), eq("0"));
    }

    @Test
    @DisplayName("更新：已存在配置 → UPDATE 成功返回 200")
    void update_existing_row() {
        when(jdbcTemplate.update(contains("UPDATE sys_config"), eq("1500"))).thenReturn(1);

        Map<String, Object> r = service.updateTokenPerPoint(1500);

        assertEquals(200, r.get("code"));
        assertEquals(1500, r.get("tokenPerPoint"));
        verify(jdbcTemplate, never()).update(contains("INSERT INTO sys_config"), eq("1500"));
    }

    @Test
    @DisplayName("更新：配置不存在 → INSERT 后返回 200")
    void update_missing_row_inserts() {
        when(jdbcTemplate.update(contains("UPDATE sys_config"), eq("800"))).thenReturn(0);
        when(jdbcTemplate.update(contains("INSERT INTO sys_config"), eq("800"))).thenReturn(1);

        Map<String, Object> r = service.updateTokenPerPoint(800);

        assertEquals(200, r.get("code"));
        verify(jdbcTemplate).update(contains("INSERT INTO sys_config"), eq("800"));
    }
}
