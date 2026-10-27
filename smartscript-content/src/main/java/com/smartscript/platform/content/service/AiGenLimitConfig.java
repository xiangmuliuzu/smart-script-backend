package com.smartscript.platform.content.service;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 生成字数上限配置（防平台亏损）。
 *
 * 存储于 sys_config，key = {@code ai.gen.max.{capability}}（capability ∈ write/polish/outline），
 * 管理员可在「AI 创作与次数」页面调整；值为 0 表示不限。
 * 默认值：大纲 1000 字、写作 2000 字、润色 2000 字。
 *
 * @author xiangsipeng
 */
@Component
public class AiGenLimitConfig
{
    private static final Logger log = LoggerFactory.getLogger(AiGenLimitConfig.class);

    /** 大纲默认上限（字） */
    public static final int DEFAULT_OUTLINE = 1000;

    /** 写作默认上限（字） */
    public static final int DEFAULT_WRITE = 2000;

    /** 润色默认上限（字） */
    public static final int DEFAULT_POLISH = 2000;

    /** 不限 */
    public static final int UNLIMITED = 0;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 读取指定能力的生成字数上限；配置缺失/非法回退默认值，0 表示不限。
     *
     * @param capability write / polish / outline
     */
    public int maxChars(String capability)
    {
        int def = defaultFor(capability);
        try
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = ? LIMIT 1",
                    "ai.gen.max." + capability);
            if (!rows.isEmpty() && rows.get(0).get("config_value") != null)
            {
                int v = Integer.parseInt(String.valueOf(rows.get(0).get("config_value")).trim());
                if (v >= 0)
                {
                    return v;
                }
            }
        }
        catch (Exception e)
        {
            log.warn("读取 AI 生成字数上限失败，使用默认值: {}", e.getMessage());
        }
        return def;
    }

    /** 能力对应的默认上限。 */
    public int defaultFor(String capability)
    {
        if ("outline".equals(capability))
        {
            return DEFAULT_OUTLINE;
        }
        if ("polish".equals(capability))
        {
            return DEFAULT_POLISH;
        }
        return DEFAULT_WRITE;
    }
}
