package com.smartscript.platform.user.exception;

/**
 * A4 PC 管理接口业务异常（契约 §6）。
 *
 * 通过 httpStatus 与 code 双写，使 HTTP 状态码与若依 AjaxResult.code 一致。
 * message 必须为可对外展示的安全文案，禁止携带 SQL、路径或内部类名。
 */
public class AppAdminException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final int code;
    private final int httpStatus;

    public AppAdminException(int code, int httpStatus, String message)
    {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static AppAdminException badRequest(String message)
    {
        return new AppAdminException(com.smartscript.platform.user.constant.AppAdminErrorCodes.BAD_REQUEST,
                400, message);
    }

    public static AppAdminException notFound()
    {
        return new AppAdminException(com.smartscript.platform.user.constant.AppAdminErrorCodes.NOT_FOUND, 404,
                com.smartscript.platform.user.constant.AppAdminErrorCodes.RESOURCE_NOT_FOUND);
    }

    public static AppAdminException conflict(String message)
    {
        return new AppAdminException(com.smartscript.platform.user.constant.AppAdminErrorCodes.CONFLICT, 409, message);
    }

    public static AppAdminException forbidden()
    {
        return new AppAdminException(com.smartscript.platform.user.constant.AppAdminErrorCodes.FORBIDDEN, 403,
                "权限不足");
    }

    public int getCode()
    {
        return code;
    }

    public int getHttpStatus()
    {
        return httpStatus;
    }
}
