package com.smartscript.platform.user.dto;

/**
 * A3 contract envelope: {code, message, data}.
 * Intentionally distinct from RuoYi AjaxResult (which uses "msg").
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
