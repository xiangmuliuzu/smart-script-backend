package com.smartscript.platform.user.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.user.dto.AppApiResponse;
import com.smartscript.platform.user.dto.AuthSessionDto;
import com.smartscript.platform.user.dto.CurrentUserDto;
import com.smartscript.platform.user.dto.PasswordChangeRequest;
import com.smartscript.platform.user.dto.PasswordLoginRequest;
import com.smartscript.platform.user.dto.PasswordResetRequest;
import com.smartscript.platform.user.dto.PasswordSetRequest;
import com.smartscript.platform.user.dto.RegisterRequest;
import com.smartscript.platform.user.dto.SmsLoginRequest;
import com.smartscript.platform.user.dto.SmsSendRequest;
import com.smartscript.platform.user.dto.TokenRefreshRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.security.AppIdentityContext;
import com.smartscript.platform.user.service.AppAuthenticationService;
import com.smartscript.platform.user.service.SmsCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * A3-AUTH-CONTRACT-v1 App auth endpoints under /api/v1/auth.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AppAuthController
{
    private final AppAuthenticationService authenticationService;
    private final SmsCodeService smsCodeService;
    private final com.smartscript.platform.user.config.AppAuthProperties properties;

    public AppAuthController(AppAuthenticationService authenticationService,
            SmsCodeService smsCodeService,
            com.smartscript.platform.user.config.AppAuthProperties properties)
    {
        this.authenticationService = authenticationService;
        this.smsCodeService = smsCodeService;
        this.properties = properties;
    }

    @PostMapping("/sms/send")
    public AppApiResponse<Map<String, Object>> sendSms(@Valid @RequestBody SmsSendRequest request,
            HttpServletRequest http,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId)
    {
        smsCodeService.sendCode(request.getPhone(), request.getScene(), clientIp(http), requestId);
        return AppApiResponse.ok(Map.of(
                "cooldownSeconds", properties.getSms().getCooldownSeconds(),
                "expiresIn", properties.getSms().getCodeTtlSeconds()));
    }

    @PostMapping("/sms/login")
    public AppApiResponse<AuthSessionDto> smsLogin(@Valid @RequestBody SmsLoginRequest request, HttpServletRequest http)
    {
        AuthSessionDto session = authenticationService.smsLogin(request, clientIp(http));
        return AppApiResponse.ok(session);
    }

    @PostMapping("/password/login")
    public AppApiResponse<AuthSessionDto> passwordLogin(@Valid @RequestBody PasswordLoginRequest request,
            HttpServletRequest http)
    {
        return AppApiResponse.ok(authenticationService.passwordLogin(request, clientIp(http)));
    }

    @PostMapping("/register")
    public AppApiResponse<AuthSessionDto> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http)
    {
        return AppApiResponse.ok(authenticationService.register(request, clientIp(http)));
    }

    @PostMapping("/token/refresh")
    public AppApiResponse<AuthSessionDto> refresh(@Valid @RequestBody TokenRefreshRequest request)
    {
        return AppApiResponse.ok(authenticationService.refresh(request));
    }

    @PostMapping("/logout")
    public AppApiResponse<Void> logout()
    {
        authenticationService.logout(currentIdentity());
        return AppApiResponse.ok();
    }

    @GetMapping("/me")
    public AppApiResponse<CurrentUserDto> me()
    {
        return AppApiResponse.ok(authenticationService.me(currentIdentity()));
    }

    @PostMapping("/password/set")
    public AppApiResponse<Void> setPassword(@Valid @RequestBody PasswordSetRequest request)
    {
        authenticationService.setPassword(currentIdentity(), request.getPassword());
        return AppApiResponse.ok();
    }

    @PostMapping("/password/change")
    public AppApiResponse<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request)
    {
        authenticationService.changePassword(currentIdentity(), request);
        return AppApiResponse.ok();
    }

    @PostMapping("/password/reset")
    public AppApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request)
    {
        authenticationService.resetPassword(request);
        return AppApiResponse.ok();
    }

    @GetMapping("/agreements")
    public AppApiResponse<Map<String, Object>> agreements()
    {
        return AppApiResponse.ok(authenticationService.agreementsPayload());
    }

    @PostMapping("/oauth/{provider}/login")
    public AppApiResponse<Void> oauthLogin(@PathVariable("provider") String provider)
    {
        authenticationService.oauthNotOpen(provider);
        return AppApiResponse.fail(50101, "oauth provider not open");
    }

    private AppIdentityContext currentIdentity()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AppIdentityContext identity)
        {
            return identity;
        }
        throw new AppAuthException(40100, 401, "unauthorized");
    }

    private static String clientIp(HttpServletRequest request)
    {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank())
        {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        return request.getRemoteAddr();
    }
}
