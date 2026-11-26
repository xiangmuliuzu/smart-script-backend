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

/**
 * AI 生成字数上限配置读取单元测试。
 *
 * 验证 sys_config（ai.gen.max_tokens.*）读取：有配置用配置、缺失/非法回退默认值、
 * 0 表示不限；各能力额度相互独立。
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
    @DisplayName("token 上限：设定/事件卡/正文各自独立读取")
    void maxTokens_configuredIndependently()
    {
        when(jdbcTemplate.queryForList(anyString(), eq("ai.gen.max_tokens.setting")))
                .thenReturn(singleRow("12000"));
        when(jdbcTemplate.queryForList(anyString(), eq("ai.gen.max_tokens.episode")))
                .thenReturn(singleRow("6000"));
        when(jdbcTemplate.queryForList(anyString(), eq("ai.gen.max_tokens.write")))
                .thenReturn(singleRow("4000"));
        assertEquals(12000, config.maxTokens("setting"));
        assertEquals(6000, config.maxTokens("episode"));
        assertEquals(4000, config.maxTokens("write"));
    }

    @Test
    @DisplayName("token 上限缺失：回退 8192（设定与正文互不影响）")
    void maxTokens_missing_fallback8192()
    {
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(Collections.emptyList());
        assertEquals(8192, config.maxTokens("setting"));
        assertEquals(8192, config.maxTokens("episode"));
        assertEquals(8192, config.maxTokens("write"));
    }

    @Test
    @DisplayName("token 上限配置为0：返回不限（0）")
    void maxTokens_zero_unlimited()
    {
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(singleRow("0"));
        assertEquals(0, config.maxTokens("setting"));
    }

    private static java.util.List<Map<String, Object>> singleRow(String value)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("config_value", value);
        return Collections.singletonList(row);
    }
}
