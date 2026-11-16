package com.smartscript.platform.user.dto;

public class AuthSessionDto
{
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    private CurrentUserDto user;

    public CurrentUserDto getUser()
    {
        return user;
    }

    public void setUser(CurrentUserDto user)
    {
        this.user = user;
    }
}
