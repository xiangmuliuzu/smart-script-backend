package com.smartscript.platform.user.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.AgreementAcceptanceDto;
import com.smartscript.platform.user.dto.AuthSessionDto;
import com.smartscript.platform.user.dto.CurrentUserDto;
import com.smartscript.platform.user.dto.PasswordChangeRequest;
import com.smartscript.platform.user.dto.PasswordLoginRequest;
import com.smartscript.platform.user.dto.PasswordResetRequest;
import com.smartscript.platform.user.dto.RegisterRequest;
import com.smartscript.platform.user.dto.SmsLoginRequest;
import com.smartscript.platform.user.dto.TokenRefreshRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.security.AppIdentityContext;
import com.smartscript.platform.user.service.RefreshSessionService.IssuedSession;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.user.util.AppHashes;

@Service
public class AppAuthenticationService
{
    private final AppUserMapper userMapper;
    private final SmsCodeService smsCodeService;
    private final AgreementService agreementService;
    private final RefreshSessionService refreshSessionService;
    private final AppSessionRevocationService revocationService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final com.smartscript.platform.identity.IdentityProvider identityProvider;
    /**
     * H15-REV-03（§9.3）：登录成功/失败、禁用账号登录的安全审计。可为 null（单测构造兜底）。
     * 事件落 sys_logininfor：actor 为掩码手机号，detail 只含场景/原因/用户 id，绝不含口令/验证码/令牌。
     */
    private final AppSecurityEventRecorder securityEvents;

    public AppAuthenticationService(AppUserMapper userMapper,
            SmsCodeService smsCodeService,
            AgreementService agreementService,
            RefreshSessionService refreshSessionService,
            AppSessionRevocationService revocationService,
            BCryptPasswordEncoder passwordEncoder,
            com.smartscript.platform.identity.IdentityProvider identityProvider,
            AppSecurityEventRecorder securityEvents)
    {
        this.userMapper = userMapper;
        this.smsCodeService = smsCodeService;
        this.agreementService = agreementService;
        this.refreshSessionService = refreshSessionService;
        this.revocationService = revocationService;
        this.passwordEncoder = passwordEncoder;
        this.identityProvider = identityProvider;
        this.securityEvents = securityEvents;
    }

    private void audit(String actorMaskedPhone, String ip, String event, String detail)
    {
        if (securityEvents != null)
        {
            securityEvents.record(actorMaskedPhone, ip, event, detail);
        }
    }

    /**
     * H15-REV-05：成功事件必须对应**已成功提交**的会话。事务内注册 afterCommit 回调——
     * 会话签发失败或事务回滚时回调不触发，不产生虚假成功行；无事务上下文时（单测/非事务路径）直接落行。
     * 失败类事件（{@link #audit}）不走此路径：失败尝试本身必须留痕，异步落库不受回滚影响。
     */
    private void auditLoginSuccessAfterCommit(String actorMaskedPhone, String ip, String detail)
    {
        if (securityEvents == null)
        {
            return;
        }
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
        {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization()
                    {
                        @Override
                        public void afterCommit()
                        {
                            securityEvents.recordSuccess(actorMaskedPhone, ip, "APP_LOGIN_SUCCESS", detail);
                        }
                    });
        }
        else
        {
            securityEvents.recordSuccess(actorMaskedPhone, ip, "APP_LOGIN_SUCCESS", detail);
        }
    }

    private static String maskPhone(String phone)
    {
        return phone == null ? "***" : AppHashes.maskPhone(phone);
    }

    @Transactional
    public AuthSessionDto smsLogin(SmsLoginRequest request, String ip)
    {
        try
        {
            smsCodeService.consumeOnce(request.getPhone(), "LOGIN", request.getCode());
        }
        catch (AppAuthException e)
        {
            // H15-REV-03：验证码失败即登录失败，须留安全审计（事件关键字 + 失败原因码），随后原样抛出
            audit(maskPhone(request.getPhone()), ip, "APP_LOGIN_FAIL", "scene=LOGIN reason=" + e.getCode());
            throw e;
        }
        AppUserRecord user = userMapper.selectByPhone(request.getPhone());
        if (user == null)
        {
            agreementService.validateCurrent(request.getAgreementAcceptances());
            user = createAppUser(request.getPhone(), null);
            agreementService.recordAcceptances(user.getUserId(), request.getAgreementAcceptances(), ip, request.getDeviceId());
        }
        else if (!user.isUsable())
        {
            audit(maskPhone(request.getPhone()), ip, "APP_LOGIN_DISABLED", "scene=LOGIN userId=" + user.getUserId());
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "account disabled");
        }
        else if (request.getAgreementAcceptances() != null && !request.getAgreementAcceptances().isEmpty()
                && !agreementService.hasCurrentConsents(user.getUserId()))
        {
            agreementService.recordAcceptances(user.getUserId(), request.getAgreementAcceptances(), ip, request.getDeviceId());
        }
        auditLoginSuccessAfterCommit(maskPhone(request.getPhone()), ip, "scene=LOGIN userId=" + user.getUserId());
        return issueSession(user, request.getDeviceId(), request.getDeviceName(), ip);
    }

    @Transactional
    public AuthSessionDto passwordLogin(PasswordLoginRequest request, String ip)
    {
        AppUserRecord user = userMapper.selectByPhone(request.getPhone());
        // Same error for unknown phone and wrong password (anti-enumeration).
        if (user == null || user.getPassword() == null || user.getPassword().isBlank()
                || !passwordEncoder.matches(request.getPassword(), user.getPassword()))
        {
            audit(maskPhone(request.getPhone()), ip, "APP_LOGIN_FAIL", "scene=PASSWORD reason=BAD_CREDENTIAL");
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "phone or password incorrect");
        }
        if (!user.isUsable())
        {
            audit(maskPhone(request.getPhone()), ip, "APP_LOGIN_DISABLED", "scene=PASSWORD userId=" + user.getUserId());
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "account disabled");
        }
        auditLoginSuccessAfterCommit(maskPhone(request.getPhone()), ip, "scene=PASSWORD userId=" + user.getUserId());
        return issueSession(user, request.getDeviceId(), request.getDeviceName(), ip);
    }

    /**
     * A 模块 PC 统一登录的 App 域入口：01/02/03 账号在 PC 网页上凭用户名或手机号 + 密码登录，
     * 签发 App 凭证域令牌（与 App 端同一套 access/refresh 体系），绝不签发若依管理端 Token。
     * 账号域判定由调用方（PcUnifiedLoginController）依据 user_type 完成，此处再防御一次：
     * 非 01/02/03 一律按凭据错误处理，避免管理端账号误入 App 凭证域。
     */
    @Transactional
    public AuthSessionDto pcPasswordLogin(String identifier, String password, String ip)
    {
        AppUserRecord user = userMapper.selectByLoginIdentifier(identifier);
        boolean credentialOk = user != null
                && com.smartscript.platform.user.constant.AppAdminConstants.isManagedUserType(user.getUserType())
                && user.getPassword() != null && !user.getPassword().isBlank()
                && passwordEncoder.matches(password, user.getPassword());
        if (!credentialOk)
        {
            audit(identifier, ip, "APP_LOGIN_FAIL", "scene=PC_PASSWORD reason=BAD_CREDENTIAL");
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "用户不存在/密码错误");
        }
        if (!user.isUsable())
        {
            audit(identifier, ip, "APP_LOGIN_DISABLED", "scene=PC_PASSWORD userId=" + user.getUserId());
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "账号已停用，请联系管理员");
        }
        auditLoginSuccessAfterCommit(identifier, ip, "scene=PC_PASSWORD userId=" + user.getUserId());
        return issueSession(user, "pc-web", "PC Web", ip);
    }

    @Transactional
    public AuthSessionDto register(RegisterRequest request, String ip)
    {
        validatePasswordPolicy(request.getPassword());
        smsCodeService.consumeOnce(request.getPhone(), "REGISTER", request.getCode());
        agreementService.validateCurrent(request.getAgreementAcceptances());
        AppUserRecord existing = userMapper.selectByPhone(request.getPhone());
        if (existing != null)
        {
            throw new AppAuthException(AppAuthErrorCodes.PHONE_TAKEN, 409, "phone already registered");
        }
        AppUserRecord user = createAppUser(request.getPhone(), passwordEncoder.encode(request.getPassword()));
        agreementService.recordAcceptances(user.getUserId(), request.getAgreementAcceptances(), ip, request.getDeviceId());
        return issueSession(user, request.getDeviceId(), request.getDeviceName(), ip);
    }

    public AuthSessionDto refresh(TokenRefreshRequest request, String ip)
    {
        IssuedSession session = refreshSessionService.rotate(request.getRefreshToken(), request.getDeviceId(), request.getDeviceName(), ip);
        AppUserRecord user = userMapper.selectById(session.getUserId());
        if (user == null || !user.isUsable())
        {
            audit("user-" + session.getUserId(), ip, "APP_LOGIN_DISABLED", "scene=REFRESH userId=" + session.getUserId());
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "account disabled");
        }
        return toAuthSession(session, user);
    }

    public void logout(AppIdentityContext identity)
    {
        if (identity == null)
        {
            return;
        }
        revocationService.revokeSession(identity.getSessionId(), identity.getJti(), "LOGOUT");
    }

    public CurrentUserDto me(AppIdentityContext identity)
    {
        AppUserRecord user = userMapper.selectById(identity.getUserId());
        if (user == null || !user.isUsable())
        {
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "account disabled");
        }
        return toCurrentUser(user);
    }

    @Transactional
    public void setPassword(AppIdentityContext identity, String password)
    {
        validatePasswordPolicy(password);
        AppUserRecord user = requireUser(identity.getUserId());
        if (user.getPassword() != null && !user.getPassword().isBlank())
        {
            throw new AppAuthException(AppAuthErrorCodes.PASSWORD_STATE_CONFLICT, 409, "password already set");
        }
        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(password));
        revocationService.revokeOthersForUser(user.getUserId(), identity.getSessionId(), "PASSWORD_SET");
    }

    @Transactional
    public void changePassword(AppIdentityContext identity, PasswordChangeRequest request)
    {
        validatePasswordPolicy(request.getNewPassword());
        AppUserRecord user = requireUser(identity.getUserId());
        if (user.getPassword() == null || user.getPassword().isBlank()
                || !passwordEncoder.matches(request.getOldPassword(), user.getPassword()))
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "old password incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword()))
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "new password must differ");
        }
        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(request.getNewPassword()));
        revocationService.revokeAllForUser(user.getUserId(), "PASSWORD_CHANGE");
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request)
    {
        validatePasswordPolicy(request.getNewPassword());
        smsCodeService.consumeOnce(request.getPhone(), "RESET_PASSWORD", request.getCode());
        AppUserRecord user = userMapper.selectByPhone(request.getPhone());
        if (user == null)
        {
            // Do not reveal existence; still consume code path for security tests via same message.
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "phone or password incorrect");
        }
        if (user.getPassword() != null && passwordEncoder.matches(request.getNewPassword(), user.getPassword()))
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "new password must differ");
        }
        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(request.getNewPassword()));
        revocationService.revokeAllForUser(user.getUserId(), "PASSWORD_RESET");
    }

    public void oauthNotOpen(String provider)
    {
        if (!"wechat".equals(provider) && !"qq".equals(provider))
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "unsupported oauth provider");
        }
        throw new AppAuthException(AppAuthErrorCodes.OAUTH_NOT_OPEN, 501, "oauth provider not open");
    }

    private AppUserRecord createAppUser(String phone, String encodedPassword)
    {
        AppUserRecord user = new AppUserRecord();
        user.setUserName(randomAppUserName());
        user.setNickName(AppHashes.maskPhone(phone));
        user.setPhonenumber(phone);
        user.setPassword(encodedPassword == null ? "" : encodedPassword);
        user.setUserType("01");
        user.setAvatar("");
        user.setStatus("0");
        user.setDelFlag("0");
        try
        {
            userMapper.insertAppUser(user);
        }
        catch (DuplicateKeyException e)
        {
            AppUserRecord existing = userMapper.selectByPhone(phone);
            if (existing == null)
            {
                throw e;
            }
            return existing;
        }
        return user;
    }

    private AuthSessionDto issueSession(AppUserRecord user, String deviceId, String deviceName, String ip)
    {
        userMapper.updateLoginInfo(user.getUserId(), ip, new Date());
        IssuedSession session = refreshSessionService.createSession(user.getUserId(), deviceId, deviceName);
        return toAuthSession(session, user);
    }

    private AuthSessionDto toAuthSession(IssuedSession session, AppUserRecord user)
    {
        AuthSessionDto dto = new AuthSessionDto();
        dto.setAccessToken(session.getAccessToken().getToken());
        dto.setRefreshToken(session.getRawRefreshToken());
        dto.setTokenType("Bearer");
        dto.setExpiresIn(session.getAccessToken().getExpiresIn());
        dto.setRefreshExpiresIn(session.getRefreshExpiresIn());
        dto.setUser(toCurrentUser(user));
        return dto;
    }

    private CurrentUserDto toCurrentUser(AppUserRecord user)
    {
        CurrentUserDto dto = new CurrentUserDto();
        dto.setUserId(user.getUserId());
        dto.setUserType(normalizeUserType(user.getUserType()));
        dto.setNickname(user.getNickName());
        dto.setAvatar(user.getAvatar() == null || user.getAvatar().isBlank() ? null : user.getAvatar());
        dto.setPhoneMasked(AppHashes.maskPhone(user.getPhonenumber()));
        // A5 起 /auth/me 返回真实角色与实名状态，供 App 统一身份能力使用
        // （规格 §10：AuthState.currentUser.realNameStatus 与 hasRole）。
        // A6 起补充权限标识与作者能力，并复用统一身份解析结果，避免同一份数据两处查询。
        IdentityContext identity = identityProvider.currentIdentity();
        dto.setRoles(identity.getRoleCodes().toArray(new String[0]));
        dto.setPermissions(identity.getPermissionCodes().toArray(new String[0]));
        dto.setAuthorCapability(identity.isAuthorCapability());
        dto.setRealNameStatus(identity.getRealNameStatus());
        dto.setHasPassword(user.getPassword() != null && !user.getPassword().isBlank());
        return dto;
    }

    private AppUserRecord requireUser(Long userId)
    {
        AppUserRecord user = userMapper.selectById(userId);
        if (user == null || !user.isUsable())
        {
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "account disabled");
        }
        return user;
    }

    static String normalizeUserType(String userType)
    {
        if ("01".equals(userType) || "02".equals(userType) || "03".equals(userType))
        {
            return userType;
        }
        return "01";
    }

    public static void validatePasswordPolicy(String password)
    {
        if (password == null || password.length() < 8 || password.length() > 64)
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "password length must be 8-64");
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++)
        {
            char c = password.charAt(i);
            if (Character.isLetter(c))
            {
                letter = true;
            }
            else if (Character.isDigit(c))
            {
                digit = true;
            }
        }
        if (!letter || !digit)
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "password must contain letter and digit");
        }
    }

    private static String randomAppUserName()
    {
        StringBuilder sb = new StringBuilder("app_");
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++)
        {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    public Map<String, Object> agreementsPayload()
    {
        return Map.of("agreements", agreementService.currentAgreements());
    }
}
