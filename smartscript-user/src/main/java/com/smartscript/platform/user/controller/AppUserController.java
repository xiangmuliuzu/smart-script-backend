package com.smartscript.platform.user.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.user.dto.NotificationPreferenceUpdateRequest;
import com.smartscript.platform.user.dto.PhoneChangeConfirmRequest;
import com.smartscript.platform.user.dto.PhoneChangeNewPhoneRequest;
import com.smartscript.platform.user.dto.PhoneChangeVerifyRequest;
import com.smartscript.platform.user.dto.PhoneStepUpResult;
import com.smartscript.platform.user.dto.RealNameStatusDto;
import com.smartscript.platform.user.dto.RealNameSubmitRequest;
import com.smartscript.platform.user.dto.UserProfileDto;
import com.smartscript.platform.user.dto.UserProfileUpdateRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.security.AppIdentityContext;
import com.smartscript.platform.user.service.AppFileUploadService;
import com.smartscript.platform.user.service.AppPhoneChangeService;
import com.smartscript.platform.user.service.AppRealNameService;
import com.smartscript.platform.user.service.AppUserProfileService;
import com.smartscript.platform.user.service.UserMessageService;
import com.smartscript.platform.user.util.AppClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * A5 App 用户中心：资料 / 实名 / 账号安全 / 通知偏好
 * （契约 A5-USER-CENTER-CONTRACT-v1 §1.2–§1.5）。
 *
 * 鉴权：/api/v1/users/** 由 App 凭证域的 Spring Security 链保护，
 * 因此本控制器不重复做登录判定，只从安全上下文取 AppIdentityContext。
 * 业务接口一律以身份上下文中的 userId 为准，不接受请求体传入用户标识。
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class AppUserController
{
    private final AppUserProfileService profileService;
    private final AppRealNameService realNameService;
    private final AppPhoneChangeService phoneChangeService;
    private final UserMessageService messageService;
    private final AppFileUploadService uploadService;

    public AppUserController(AppUserProfileService profileService,
            AppRealNameService realNameService,
            AppPhoneChangeService phoneChangeService,
            UserMessageService messageService,
            AppFileUploadService uploadService)
    {
        this.profileService = profileService;
        this.realNameService = realNameService;
        this.phoneChangeService = phoneChangeService;
        this.messageService = messageService;
        this.uploadService = uploadService;
    }

    // ------------------------------------------------------------------
    // 个人资料（§1.2）
    // ------------------------------------------------------------------

    @GetMapping("/me/profile")
    public AppApiResponse<UserProfileDto> profile()
    {
        return AppApiResponse.ok(profileService.getProfile(currentUserId()));
    }

    @PutMapping("/me/profile")
    public AppApiResponse<UserProfileDto> updateProfile(@RequestBody UserProfileUpdateRequest request)
    {
        return AppApiResponse.ok(profileService.updateProfile(currentUserId(), request));
    }

    /**
     * 头像图片上传（规格 §8.3）。
     *
     * App 域专用端点：平台原生的 `/common/upload` 属于 PC 凭证链，
     * App Access Token 在其上无法通过鉴权，且其响应格式与 App 信封不一致。
     * 返回 `{ "url": "...", "path": "..." }`，url 可直接用于资料更新的 avatar 字段。
     */
    @PostMapping("/me/avatar")
    public AppApiResponse<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file)
    {
        return AppApiResponse.ok(uploadService.uploadImage(file));
    }

    // ------------------------------------------------------------------
    // 实名认证（§1.3）
    // ------------------------------------------------------------------

    @GetMapping("/me/real-name")
    public AppApiResponse<RealNameStatusDto> realName()
    {
        return AppApiResponse.ok(realNameService.getStatus(currentUserId()));
    }

    @PostMapping("/me/real-name")
    public AppApiResponse<RealNameStatusDto> submitRealName(@Valid @RequestBody RealNameSubmitRequest request)
    {
        return AppApiResponse.ok(realNameService.submit(currentUserId(), request));
    }

    @PostMapping("/me/real-name/resubmit")
    public AppApiResponse<RealNameStatusDto> resubmitRealName(@Valid @RequestBody RealNameSubmitRequest request)
    {
        return AppApiResponse.ok(realNameService.resubmit(currentUserId(), request));
    }

    // ------------------------------------------------------------------
    // 账号安全——换绑手机号（§1.4）
    // ------------------------------------------------------------------

    @PostMapping("/me/phone/change/old/send")
    public AppApiResponse<Map<String, Object>> sendOldPhoneCode(HttpServletRequest http,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId)
    {
        // 号码由服务端按当前身份取：不接受请求体指定发往哪个号码
        return AppApiResponse.ok(
                phoneChangeService.sendOldPhoneCode(currentIdentity(), AppClientIp.resolve(http), requestId));
    }

    @PostMapping("/me/phone/change/old/verify")
    public AppApiResponse<PhoneStepUpResult> verifyOldPhone(@Valid @RequestBody PhoneChangeVerifyRequest request,
            HttpServletRequest http,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId)
    {
        return AppApiResponse.ok(
                phoneChangeService.verifyOldPhone(currentIdentity(), request, AppClientIp.resolve(http), requestId));
    }

    @PostMapping("/me/phone/change/new/send")
    public AppApiResponse<Map<String, Object>> sendNewPhoneCode(@Valid @RequestBody PhoneChangeNewPhoneRequest request,
            HttpServletRequest http,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId)
    {
        // 新号验证码走用户中心的专用端点，而不复用 /auth/sms/send：
        // 该端点需要在发码前完成「新号未被占用」判定与身份绑定，
        // 复用公开发码端点会把这两项校验分散到两个入口。
        return AppApiResponse.ok(
                phoneChangeService.sendNewPhoneCode(currentIdentity(), request, AppClientIp.resolve(http), requestId));
    }

    @PostMapping("/me/phone/change/confirm")
    public AppApiResponse<Map<String, Object>> confirmPhoneChange(
            @Valid @RequestBody PhoneChangeConfirmRequest request, HttpServletRequest http)
    {
        return AppApiResponse.ok(
                phoneChangeService.confirm(currentIdentity(), request, AppClientIp.resolve(http)));
    }

    // ------------------------------------------------------------------
    // 通知偏好（§1.5）
    // ------------------------------------------------------------------

    @GetMapping("/me/notification-preferences")
    public AppApiResponse<Map<String, Object>> notificationPreferences()
    {
        return AppApiResponse.ok(messageService.getPreferences(currentUserId()));
    }

    @PutMapping("/me/notification-preferences")
    public AppApiResponse<Map<String, Object>> updateNotificationPreferences(
            @Valid @RequestBody NotificationPreferenceUpdateRequest request)
    {
        return AppApiResponse.ok(messageService.updatePreferences(currentUserId(), request));
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    private AppIdentityContext currentIdentity()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AppIdentityContext identity)
        {
            return identity;
        }
        // 到达这里说明过滤器链未建立 App 身份（配置错误或凭证域不符）
        throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "unauthorized");
    }

    private Long currentUserId()
    {
        return currentIdentity().getUserId();
    }
}
