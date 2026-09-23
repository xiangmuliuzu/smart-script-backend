package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * A5 换绑第 3 步：确认换绑（契约 §1.4）。
 *
 * 必须同时持有一
 *   - 旧号验证产生的一次性 stepUpToken（证明本人持旧号），且绑定同一设备；
 *   - 新号收到的验证码（证明本人持新号）。
 * 两步凭证都短时且一次性；服务端只存散列/缓存，不落库。
 */
public class PhoneChangeConfirmRequest
{
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$", message = "phone")
    private String newPhone;

    @NotBlank
    private String code;

    @NotBlank
    private String stepUpToken;

    private String deviceId;

    public String getNewPhone()
    {
        return newPhone;
    }

    public void setNewPhone(String newPhone)
    {
        this.newPhone = newPhone;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getStepUpToken()
    {
        return stepUpToken;
    }

    public void setStepUpToken(String stepUpToken)
    {
        this.stepUpToken = stepUpToken;
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
