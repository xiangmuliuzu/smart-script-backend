package com.smartscript.platform.content.exception;

/**
 * PC 用户端接口未登录异常（A2）。
 *
 * 由 {@code AppUserWorkController} 在 IdentityProvider 未解析到用户时抛出，
 * 全局异常处理器映射为 401。
 *
 * @author smartscript
 */
public class AppUserWorkUnauthorizedException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AppUserWorkUnauthorizedException()
    {
        super("未登录或登录已失效");
    }
}
