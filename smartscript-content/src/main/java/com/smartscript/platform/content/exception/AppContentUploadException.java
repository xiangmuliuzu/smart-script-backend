package com.smartscript.platform.content.exception;

/**
 * App 内容域上传异常（内容模块本地异常，携带 App 信封业务码）。
 *
 * 为什么不复用 A 模块的 AppAuthException：smartscript-content 刻意不依赖
 * smartscript-user（见模块 pom），故在本模块内定义最小异常，由控制器就地
 * 收敛为 App 契约响应，不引入跨模块编译期耦合。
 *
 * @author xiangsipeng
 */
public class AppContentUploadException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** App 信封业务码（400 入参非法 / 413 超限 / 500 服务端失败） */
    private final int code;

    public AppContentUploadException(int code, String message)
    {
        super(message);
        this.code = code;
    }

    public int getCode()
    {
        return code;
    }
}