package com.smartscript.platform.content.service;

/**
 * AI 调用结果：完整内容 + 本次调用实际消耗的 token 数（来自 DeepSeek usage.total_tokens）。
 *
 * 去次数化后，AI 创作按实际 token 消耗扣减积分：调用方拿到 totalTokens 后
 * 按管理员配置「1积分 = N token」换算应扣积分，保证扣分准确。
 *
 * truncated：上游以 finish_reason=length 结束（输出被长度上限截断）时为 true。
 * 这类结果正文往往停在半句，必须回传前端提示重试，不能当成正常生成。
 */
public class AiCallResult
{
    /** 完整内容（流式累积全文或非流式 content） */
    private final String content;

    /** 本次调用消耗的总 token 数（usage.total_tokens；响应无 usage 时为 0） */
    private final int totalTokens;

    /** 模型名（AI 返回；缺失为配置模型） */
    private final String model;

    /** 是否因输出长度上限被截断（finish_reason=length） */
    private final boolean truncated;

    public AiCallResult(String content, int totalTokens, String model)
    {
        this(content, totalTokens, model, false);
    }

    public AiCallResult(String content, int totalTokens, String model, boolean truncated)
    {
        this.content = content;
        this.totalTokens = totalTokens;
        this.model = model;
        this.truncated = truncated;
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

    public boolean isTruncated()
    {
        return truncated;
    }
}
