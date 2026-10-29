package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmailCodeRequest
{
    @NotBlank @Size(max = 50)
    private String email;
    private String scene;
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
