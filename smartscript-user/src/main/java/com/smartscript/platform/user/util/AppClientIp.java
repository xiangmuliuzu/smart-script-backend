package com.smartscript.platform.user.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析（A3/A5 共用）。
 *
 * 与 A3 {@code AppAuthController} 的私有实现保持同一口径：优先取
 * {@code X-Forwarded-For} 的第一段（代理链最左端为真实客户端），
 * 否则回落 {@code getRemoteAddr()}。
 *
 * 该值只用于审计与频控，不作为授权依据。
 */
public final class AppClientIp
{
    private AppClientIp() {
    }

    public static String resolve(HttpServletRequest request)
    {
        if (request == null)
        {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank())
        {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
