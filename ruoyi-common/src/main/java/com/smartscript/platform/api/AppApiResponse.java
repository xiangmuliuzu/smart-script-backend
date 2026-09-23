package com.smartscript.platform.api;

/**
 * App 契约响应信封：{code, message, data}。
 *
 * 刻意区别于若依 AjaxResult（后者用 "msg"）：
 *   - App 与 PC 两个凭证域各自保持自己的响应结构（规格 §7.4）；
 *   - 本类放在公共内核（ruoyi-common），使 A6 之后的下游业务模块
 *     （smartscript-content 等）复用同一份信封定义，而不是各写一份重复实现。
 *
 * A3 起用于 /api/v1/auth、A5 用于用户中心、A6 起由内容等业务模块复用。
 */
public class AppApiResponse<T>
{
    private int code;
    private String message;
    private T data;

    public AppApiResponse()
    {
    }

    public AppApiResponse(int code, String message, T data)
    {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> AppApiResponse<T> ok(T data)
    {
        return new AppApiResponse<>(200, "ok", data);
    }

    public static <T> AppApiResponse<T> ok()
    {
        return new AppApiResponse<>(200, "ok", null);
    }

    public static <T> AppApiResponse<T> fail(int code, String message, T data)
    {
        return new AppApiResponse<>(code, message, data);
    }

    public static <T> AppApiResponse<T> fail(int code, String message)
    {
        return new AppApiResponse<>(code, message, null);
    }

    public int getCode()
    {
        return code;
    }

    public void setCode(int code)
    {
        this.code = code;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public T getData()
    {
        return data;
    }

    public void setData(T data)
    {
        this.data = data;
    }
}
