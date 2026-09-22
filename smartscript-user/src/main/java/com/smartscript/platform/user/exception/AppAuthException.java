package com.smartscript.platform.user.exception;

import java.util.Map;

public class AppAuthException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final int code;
    private final int httpStatus;
    private final Map<String, Object> data;

    public AppAuthException(int code, int httpStatus, String message)
    {
        this(code, httpStatus, message, null);
    }

    public AppAuthException(int code, int httpStatus, String message, Map<String, Object> data)
    {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = data;
    }

    public int getCode()
    {
        return code;
    }

    public int getHttpStatus()
    {
        return httpStatus;
    }

    public Map<String, Object> getData()
    {
        return data;
    }
}
