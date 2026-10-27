package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;

/**
 * AI 生成字数上限配置读取单元测试。
 *
 * 验证 sys_config（ai.gen.max.*）读取：有配置用配置、缺失/非法回退默认值、
 * 0 表示不限；不同能力默认值正确。
 */
class AiGenLimitConfigTest
{
    private JdbcTemplate jdbcTemplate;
    private AiGenLimitConfig config;

    @BeforeEach
    void setUp() throws Exception
    {
        jdbcTemplate = mock(JdbcTemplate.class);
        config = new AiGenLimitConfig();
        Field f = AiGenLimitConfig.class.getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(config, jdbcTemplate);
    }

    @Test
    @DisplayName("sys_config 有值：按配置返回")
    void configuredValue()
    {
        when(jdbcTemplate.queryForList(anyString(), eq("ai.gen.max.write")))
                .thenReturn(singleRow("1500"));
        assertEquals(1500, config.maxChars("write"));
    }

    @Test
    @DisplayName("sys_config 缺失：回退默认值（写作2000/润色2000/大纲1000）")
    void missing_fallbackDefault()
    {
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(Collections.emptyList());
        assertEquals(2000, config.maxChars("write"));
        assertEquals(2000, config.maxChars("polish"));
        assertEquals(1000, config.maxChars("outline"));
    }

    @Test
    @DisplayName("配置为0：返回不限（0）")
    void zero_unlimited()
    {
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(singleRow("0"));
        assertEquals(0, config.maxChars("write"));
    }

    @Test
    @DisplayName("配置非法/查询异常：回退默认值")
    void invalid_fallbackDefault()
    {
        when(jdbcTemplate.queryForList(anyString(), anyString()))
                .thenThrow(new RuntimeException("db down"));
        assertEquals(2000, config.maxChars("write"));
        assertEquals(1000, config.maxChars("outline"));
    }

    @Test
    @DisplayName("未知能力：按写作默认值兜底")
    void unknownCapability_writeDefault()
    {
        assertEquals(2000, config.defaultFor("unknown"));
    }

    private static java.util.List<Map<String, Object>> singleRow(String value)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("config_value", value);
        return Collections.singletonList(row);
    }
}
