package com.ruoyi.web.controller.pc;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysUserService;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.service.AppAuthenticationService;
import com.smartscript.platform.user.service.AppSecurityEventRecorder;
import com.smartscript.platform.user.service.SmsCodeService;

/**
 * A 模块：运维管理端「忘记密码」自助找回。
 *
 * <p>管理端账号是若依 {@code sys_user} 中 {@code user_type='00'} 的行，与 App 用户共用一张表，
 * 但令牌体系（管理端 Token）和密码入参口径（若依登录不校验 App 密码策略）都不同，
 * 因此不能复用 {@code /api/v1/auth/password/reset}（它只接受 App 域账号并签发 App 会话）。
 * 这里复用 App 域的短信验证码能力（{@link SmsCodeService}，scene=RESET_PASSWORD）完成身份校验，
 * 再用若依 {@link ISysUserService#resetPwd} 写回 BCrypt 密码，保证管理端登录链路不用改。</p>
 *
 * <p>安全口径：两个接口都是匿名可达（见 {@code AppAuthSecurityConfig} 与
 * {@code AppAuthAuthenticationFilter}），所以</p>
 * <ul>
 *   <li>发送验证码对「手机号未绑定管理员账号」也返回成功，避免匿名枚举管理员手机号；</li>
 *   <li>重置接口对「账号不存在」与「验证码错误/过期」返回同一句失败文案；</li>
 *   <li>只处理 {@code user_type='00'} 且未删除、状态正常的账号，不会成为改写 App 用户密码的旁路；</li>
 *   <li>重置成功写一条安全审计事件（手机号掩码，不含密码与验证码）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/pc-auth/admin")
public class PcAdminRecoveryController
{
    private static final Logger log = LoggerFactory.getLogger(PcAdminRecoveryController.class);

    /** 与 App 端「忘记密码」共用同一 scene，验证码只在本场景内可消费。 */
    private static final String RESET_SCENE = "RESET_PASSWORD";

    private static final String PHONE_PATTERN = "^1\\d{10}$";

    /** 账号不存在与验证码失败共用同一句文案，避免匿名调用方据此判断手机号是否绑定管理员账号。 */
    private static final String GENERIC_RESET_FAIL = "手机号或验证码不正确，请重新获取验证码后再试";

    private static final String SMS_SENT_HINT = "若该手机号已绑定管理员账号，验证码已发送";

    private final AppUserMapper userMapper;

    private final SmsCodeService smsCodeService;

    private final ISysUserService sysUserService;

    private final AppSecurityEventRecorder securityEventRecorder;

    public PcAdminRecoveryController(AppUserMapper userMapper,
            SmsCodeService smsCodeService,
            ISysUserService sysUserService,
            AppSecurityEventRecorder securityEventRecorder)
    {
        this.userMapper = userMapper;
        this.smsCodeService = smsCodeService;
        this.sysUserService = sysUserService;
        this.securityEventRecorder = securityEventRecorder;
    }

    /** 发送找回密码短信验证码。 */
    @PostMapping("/sms/send")
    public AjaxResult sendCode(@RequestBody AdminSmsSendBody body, HttpServletRequest http)
    {
        String phone = StringUtils.trimToEmpty(body == null ? null : body.phone());
        if (!phone.matches(PHONE_PATTERN))
        {
            return AjaxResult.error(40000, "请输入正确的手机号");
        }

        AppUserRecord account = findAdminByPhone(phone);
        if (account == null)
        {
            // 不发送也不报错：匿名调用方无法区分「未绑定」与「已发送」。
            return AjaxResult.success(SMS_SENT_HINT);
        }

        try
        {
            smsCodeService.sendCode(phone, RESET_SCENE, clientIp(http), null);
        }
        catch (AppAuthException e)
        {
            // 冷却/日限/限流同样不能回显：命中频率限制本身就说明该手机号绑定过账号。
            log.warn("admin password recovery sms suppressed phone={} reason={}", maskPhone(phone), e.getCode());
        }
        catch (RuntimeException e)
        {
            log.warn("admin password recovery sms failed phone={}: {}", maskPhone(phone), e.getMessage());
        }
        return AjaxResult.success(SMS_SENT_HINT);
    }

    /** 校验短信验证码并重置管理员密码。 */
    @PostMapping("/password/reset")
    public AjaxResult resetPassword(@RequestBody AdminPasswordResetBody body, HttpServletRequest http)
    {
        String phone = StringUtils.trimToEmpty(body == null ? null : body.phone());
        String code = StringUtils.trimToEmpty(body == null ? null : body.code());
        String newPassword = body == null ? null : body.newPassword();

        if (!phone.matches(PHONE_PATTERN))
        {
            return AjaxResult.error(40000, "请输入正确的手机号");
        }
        if (code.isEmpty())
        {
            return AjaxResult.error(40000, "请输入短信验证码");
        }
        try
        {
            AppAuthenticationService.validatePasswordPolicy(newPassword);
        }
        catch (AppAuthException e)
        {
            return AjaxResult.error(40000, passwordPolicyMessage(e.getMessage()));
        }

        // 先消费验证码再看账号：两条失败路径都不再产生可区分的信息。
        boolean codeOk = consumeCode(phone, code);
        AppUserRecord account = codeOk ? findAdminByPhone(phone) : null;
        if (!codeOk || account == null)
        {
            return AjaxResult.error(40000, GENERIC_RESET_FAIL);
        }

        if (account.getPassword() != null && SecurityUtils.matchesPassword(newPassword, account.getPassword()))
        {
            return AjaxResult.error(40000, "新密码不能与当前密码相同");
        }

        SysUser update = new SysUser();
        update.setUserId(account.getUserId());
        update.setPassword(SecurityUtils.encryptPassword(newPassword));
        sysUserService.resetPwd(update);

        securityEventRecorder.recordSuccess(maskPhone(phone), clientIp(http), "ADMIN_PASSWORD_RESET",
                "source=pc-admin-recovery userId=" + account.getUserId());
        return AjaxResult.success("密码已重置，请使用新密码登录");
    }

    private boolean consumeCode(String phone, String code)
    {
        try
        {
            smsCodeService.consumeOnce(phone, RESET_SCENE, code);
            return true;
        }
        catch (AppAuthException e)
        {
            log.info("admin password recovery code rejected phone={} reason={}", maskPhone(phone), e.getCode());
            return false;
        }
        catch (RuntimeException e)
        {
            log.warn("admin password recovery code check failed phone={}: {}", maskPhone(phone), e.getMessage());
            return false;
        }
    }

    /** 只认管理域账号：App 用户（01/02/03）走各自的找回入口，不能从运维端改密。 */
    private AppUserRecord findAdminByPhone(String phone)
    {
        AppUserRecord account = userMapper.selectByPhone(phone);
        if (account == null || !AppAdminConstants.USER_TYPE_PC_ADMIN.equals(account.getUserType())
                || !account.isUsable())
        {
            return null;
        }
        return account;
    }

    private static String passwordPolicyMessage(String message)
    {
        if (message == null)
        {
            return "密码不符合要求";
        }
        return switch (message)
        {
            case "password length must be 8-64" -> "密码长度需为 8–64 位";
            case "password must not exceed 72 UTF-8 bytes" -> "密码过长，请缩短后重试";
            case "password must contain letter and digit" -> "密码需同时包含字母和数字";
            default -> "密码不符合要求";
        };
    }

    private static String maskPhone(String phone)
    {
        if (phone == null || phone.length() < 7)
        {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String clientIp(HttpServletRequest request)
    {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank())
        {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 管理端发送验证码入参。 */
    public record AdminSmsSendBody(String phone)
    {
    }

    /** 管理端重置密码入参。 */
    public record AdminPasswordResetBody(String phone, String code, String newPassword)
    {
    }
}
