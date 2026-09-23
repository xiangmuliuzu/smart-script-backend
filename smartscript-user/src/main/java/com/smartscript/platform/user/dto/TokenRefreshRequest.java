package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;

public class TokenRefreshRequest
{
    @NotBlank
    private String refreshToken;

    @NotBlank
    private String deviceId;

    private String deviceName;

    public String getRefreshToken()
    {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken)
    {
        this.refreshToken = refreshToken;
    }

    public String getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(String deviceId)
    {
        this.deviceId = deviceId;
    }

    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }
}
