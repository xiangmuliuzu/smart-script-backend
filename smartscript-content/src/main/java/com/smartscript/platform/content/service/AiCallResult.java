package com.smartscript.platform.content.service;

/**
 * AI 调用结果：完整内容 + 本次调用实际消耗的 token 数（来自 DeepSeek usage.total_tokens）。
 *
 * 去次数化后，AI 创作按实际 token 消耗扣减积分：调用方拿到 totalTokens 后
 * 按管理员配置「1积分 = N token」换算应扣积分，保证扣分准确。
 */
public class AiCallResult
{
    /** 完整内容（流式累积全文或非流式 content） */
    private final String content;

    /** 本次调用消耗的总 token 数（usage.total_tokens；响应无 usage 时为 0） */
    private final int totalTokens;

    /** 模型名（AI 返回；缺失为配置模型） */
    private final String model;

    public AiCallResult(String content, int totalTokens, String model)
    {
        this.content = content;
        this.totalTokens = totalTokens;
        this.model = model;
    }

    public String getContent()
    {
        return content;
    }

    public int getTotalTokens()
    {
        return totalTokens;
    }

    public String getModel()
    {
        return model;
    }
}
