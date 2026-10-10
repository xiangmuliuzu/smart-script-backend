package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordChangeRequest
{
    @NotBlank
    @Size(min = 8, max = 64)
    private String oldPassword;

    @NotBlank
    @Size(min = 8, max = 64)
    private String newPassword;

    public String getOldPassword()
    {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword)
    {
        this.oldPassword = oldPassword;
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
