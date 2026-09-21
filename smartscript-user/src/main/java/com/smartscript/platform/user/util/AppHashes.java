package com.smartscript.platform.user.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Irreversible hashes for SMS codes and refresh tokens (SHA-256 hex, 64 chars).
 */
public final class AppHashes
{
    private static final SecureRandom RANDOM = new SecureRandom();

    private AppHashes()
    {
    }

    public static String sha256Hex(String raw)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash)
            {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString().toLowerCase(Locale.ROOT);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String randomToken(int bytes)
    {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return toHex(buf);
    }

    public static String randomNumericCode(int length)
    {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
        {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public static String maskPhone(String phone)
    {
        if (phone == null || phone.length() < 7)
        {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String toHex(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
        {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
