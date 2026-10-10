package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A5 换绑第 1 步：验证旧手机号（契约 §1.4）。
 *
 * 验证码场景固定为 CHANGE_PHONE_OLD，由服务端指定，不接受请求体指定场景。
 */
public class PhoneChangeVerifyRequest
{
    @NotBlank
    private String code;

    private String deviceId;

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(String deviceId)
    {
        this.deviceId = deviceId;
    }
}
