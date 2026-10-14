package com.smartscript.platform.content.exception;

/**
 * AI 次数不足异常（内容模块本地异常）。
 *
 * 与 {@link AiServiceUnavailableException} 同理：smartscript-content 不依赖 smartscript-review，
 * 在模块内定义最小异常，由控制器就地收敛为 App 契约响应（400 + 明确文案）。
 *
 * 约定：AI 创作（写作/润色/大纲）调用前校验用户 AI 可用次数，不足即抛出本异常，
 * 由控制器转 400，App 端据此弹出「AI 次数不足」引导（积分兑换 / 观看广告）。
 *
 * @author smartscript
 */
public class AiQuotaInsufficientException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AiQuotaInsufficientException(String message)
    {
        super(message);
    }
}
