package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * A5 换绑第 2 步：向新手机号发码（契约 §1.4）。
 *
 * 发码前服务端校验新号未被占用，避免用户走到最后一步才失败。
 */
public class PhoneChangeNewPhoneRequest
{
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$", message = "phone")
    private String newPhone;

    private String deviceId;

    public String getNewPhone()
    {
        return newPhone;
    }

    public void setNewPhone(String newPhone)
    {
        this.newPhone = newPhone;
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
