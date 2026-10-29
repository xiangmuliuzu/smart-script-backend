package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmailChangeRequest {
    @NotBlank @Size(max = 50) private String newEmail;
    private String code;
    private String stepUpToken;
    private String deviceId;
    public String getNewEmail() { return newEmail; }
    public void setNewEmail(String value) { newEmail = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getStepUpToken() { return stepUpToken; }
    public void setStepUpToken(String value) { stepUpToken = value; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String value) { deviceId = value; }
}
