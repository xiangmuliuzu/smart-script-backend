package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordLoginRequest
{
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$")
    private String phone;

    @NotBlank
    @Size(min = 8, max = 64)
    private String password;

    @NotBlank
    private String deviceId;

    private String deviceName;

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
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
