package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordResetRequest
{
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$")
    private String phone;

    @NotBlank
    @Size(min = 4, max = 8)
    private String code;

    @NotBlank
    @Size(min = 8, max = 64)
    private String newPassword;

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

    public String getNewPassword()
    {
        return newPassword;
    }

    public void setNewPassword(String newPassword)
    {
        this.newPassword = newPassword;
    }
}
