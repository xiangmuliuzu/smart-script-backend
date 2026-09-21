package com.smartscript.platform.user.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SmsLoginRequest
{
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$")
    private String phone;

    @NotBlank
    @Size(min = 4, max = 8)
    private String code;

    @NotBlank
    private String deviceId;

    private String deviceName;

    private List<AgreementAcceptanceDto> agreementAcceptances;

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

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

    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }

    public List<AgreementAcceptanceDto> getAgreementAcceptances()
    {
        return agreementAcceptances;
    }

    public void setAgreementAcceptances(List<AgreementAcceptanceDto> agreementAcceptances)
    {
        this.agreementAcceptances = agreementAcceptances;
    }
}
