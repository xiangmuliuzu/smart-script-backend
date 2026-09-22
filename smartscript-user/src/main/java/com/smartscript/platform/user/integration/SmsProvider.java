package com.smartscript.platform.user.integration;

/**
 * SMS provider boundary. Production providers must be real; mock is gated by profile.
 */
public interface SmsProvider
{
    void send(String phone, String scene, String code);
}
