package com.smartscript.platform.user.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.LoginBody;
import com.smartscript.platform.user.dto.AuthSessionDto;
import com.smartscript.platform.user.dto.CurrentUserDto;
import com.smartscript.platform.user.dto.PasswordChangeRequest;
import com.smartscript.platform.user.dto.PasswordLoginRequest;
import com.smartscript.platform.user.dto.PasswordResetRequest;
import com.smartscript.platform.user.dto.PasswordSetRequest;
import com.smartscript.platform.user.dto.RegisterRequest;
import com.smartscript.platform.user.dto.SmsLoginRequest;
import com.smartscript.platform.user.dto.SmsSendRequest;
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
    public AjaxResult sendSms(@Valid @RequestBody SmsSendRequest request,
            HttpServletRequest http,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId)
    {
        if ("DELETE_WORK".equals(request.getScene()))
        {
            throw new com.smartscript.platform.user.exception.AppAuthException(
                    com.smartscript.platform.user.constant.AppAuthErrorCodes.PARAM,
                    400, "请从作品设置中获取删除验证码");
        }
        smsCodeService.sendCode(request.getPhone(), request.getScene(), clientIp(http), requestId);
        return AjaxResult.success(Map.of(
                "cooldownSeconds", properties.getSms().getCooldownSeconds(),
                "expiresIn", properties.getSms().getCodeTtlSeconds()));
    }

    @PostMapping("/sms/login")
    public AjaxResult smsLogin(@Valid @RequestBody SmsLoginRequest request, HttpServletRequest http)
    {
        AuthSessionDto session = authenticationService.smsLogin(request, clientIp(http));
        return loginResult(session);
    }

    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody request, HttpServletRequest http)
    {
        String identifier = request.getUsername() == null ? "" : request.getUsername().trim();
        return loginResult(authenticationService.ruoyiPasswordLogin(identifier, request.getPassword(),
                request.getCode(), request.getUuid(), clientIp(http)));
    }

    private static AjaxResult loginResult(AuthSessionDto session)
    {
        AjaxResult result = AjaxResult.success();
        result.put("token", session.getToken());
        result.put("user", session.getUser());
        return result;
    }

    @ExceptionHandler(AppAuthException.class)
    public ResponseEntity<AjaxResult> authError(AppAuthException error)
    {
        AjaxResult result = AjaxResult.error(error.getCode(), error.getMessage());
        if (error.getData() != null) result.put("data", error.getData());
        return ResponseEntity.status(error.getHttpStatus()).body(result);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AjaxResult> invalidLogin(MethodArgumentNotValidException error)
    {
        AjaxResult result = AjaxResult.error(40000, "invalid request");
        var fields = error.getBindingResult().getFieldErrors().stream().collect(
                java.util.stream.Collectors.toMap(org.springframework.validation.FieldError::getField,
                        field -> field.getDefaultMessage() == null ? "invalid" : field.getDefaultMessage(), (a, b) -> a));
        result.put("data", java.util.Map.of("fieldErrors", fields));
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<AjaxResult> malformedRequest(Exception error)
    {
        return ResponseEntity.badRequest().body(AjaxResult.error(40000, "invalid request"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AjaxResult> unexpectedError(RuntimeException error)
    {
        return ResponseEntity.internalServerError().body(AjaxResult.error(50000, "internal error"));
    }

    @PostMapping("/password/login")
    public AjaxResult passwordLogin(@Valid @RequestBody PasswordLoginRequest request,
            HttpServletRequest http)
    {
        return AjaxResult.success(authenticationService.passwordLogin(request, clientIp(http)));
    }

    @PostMapping("/register")
    public AjaxResult register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http)
    {
        return loginResult(authenticationService.register(request, clientIp(http)));
    }

    @PostMapping("/logout")
    public AjaxResult logout()
    {
        authenticationService.logout(currentIdentity());
        return AjaxResult.success();
    }

    @GetMapping("/me")
    public AjaxResult me()
    {
        return AjaxResult.success(authenticationService.me(currentIdentity()));
    }

    @GetMapping("/getInfo")
    public AjaxResult getInfo()
    {
        CurrentUserDto user = authenticationService.me(currentIdentity());
        AjaxResult result = AjaxResult.success();
        result.put("user", user);
        result.put("roles", user.getRoles());
        result.put("permissions", user.getPermissions());
        return result;
    }

    @PostMapping("/password/set")
    public AjaxResult setPassword(@Valid @RequestBody PasswordSetRequest request)
    {
        authenticationService.setPassword(currentIdentity(), request.getPassword());
        return AjaxResult.success();
    }

    @PostMapping("/password/change")
    public AjaxResult changePassword(@Valid @RequestBody PasswordChangeRequest request)
    {
        authenticationService.changePassword(currentIdentity(), request);
        return AjaxResult.success();
    }

    @PostMapping("/password/reset")
    public AjaxResult resetPassword(@Valid @RequestBody PasswordResetRequest request)
    {
        authenticationService.resetPassword(request);
        return AjaxResult.success();
    }

    @GetMapping("/agreements")
    public AjaxResult agreements()
    {
        return AjaxResult.success(authenticationService.agreementsPayload());
    }

    @PostMapping("/oauth/{provider}/login")
    public AjaxResult oauthLogin(@PathVariable("provider") String provider)
    {
        authenticationService.oauthNotOpen(provider);
        return AjaxResult.error(50101, "oauth provider not open");
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
