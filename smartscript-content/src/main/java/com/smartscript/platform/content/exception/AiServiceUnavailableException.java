package com.smartscript.platform.content.exception;

/**
 * AI 服务不可用异常（内容模块本地异常）。
 *
 * 与 {@link AppContentUploadException} 同理：smartscript-content 不依赖 smartscript-user，
 * 在模块内定义最小异常，由控制器就地收敛为 App 契约响应（503 + 明确文案）。
 *
 * 约定：AI 服务转发失败时**不返回编造内容**，一律抛出本异常，由控制器转 503。
 *
 * @author xiangsipeng
 */
public class AiServiceUnavailableException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AiServiceUnavailableException(String message)
    {
        super(message);
    }
}