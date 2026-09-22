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

    public AppAuthenticationService(AppUserMapper userMapper,
            SmsCodeService smsCodeService,
            AgreementService agreementService,
            RefreshSessionService refreshSessionService,
            AppSessionRevocationService revocationService,
            BCryptPasswordEncoder passwordEncoder)
    {
        this.userMapper = userMapper;
        this.smsCodeService = smsCodeService;
        this.agreementService = agreementService;
        this.refreshSessionService = refreshSessionService;
        this.revocationService = revocationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthSessionDto smsLogin(SmsLoginRequest request, String ip)
    {
        smsCodeService.consumeOnce(request.getPhone(), "LOGIN", request.getCode());
        AppUserRecord user = userMapper.selectByPhone(request.getPhone());
        if (user == null)
        {
            agreementService.validateCurrent(request.getAgreementAcceptances());
            user = createAppUser(request.getPhone(), null);
            agreementService.recordAcceptances(user.getUserId(), request.getAgreementAcceptances(), ip, request.getDeviceId());
        }
        else if (!user.isUsable())
        {
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "account disabled");
        }
        else if (request.getAgreementAcceptances() != null && !request.getAgreementAcceptances().isEmpty()
                && !agreementService.hasCurrentConsents(user.getUserId()))
        {
            agreementService.recordAcceptances(user.getUserId(), request.getAgreementAcceptances(), ip, request.getDeviceId());
        }
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
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "phone or password incorrect");
        }
        if (!user.isUsable())
        {
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "account disabled");
        }
        return issueSession(user, request.getDeviceId(), request.getDeviceName(), ip);
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

    public AuthSessionDto refresh(TokenRefreshRequest request)
    {
        IssuedSession session = refreshSessionService.rotate(request.getRefreshToken(), request.getDeviceId(), request.getDeviceName());
        AppUserRecord user = userMapper.selectById(session.getUserId());
        if (user == null || !user.isUsable())
        {
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
        dto.setRoles(roleKeys(user.getUserId()));
        dto.setRealNameStatus(realNameStatus(user.getUserId()));
        dto.setHasPassword(user.getPassword() != null && !user.getPassword().isBlank());
        return dto;
    }

    private String[] roleKeys(Long userId)
    {
        List<String> roles = userMapper.selectRoleKeys(userId);
        return roles == null || roles.isEmpty() ? new String[] {} : roles.toArray(new String[0]);
    }

    private String realNameStatus(Long userId)
    {
        String status = userMapper.selectRealNameStatus(userId);
        return status == null || status.isBlank() ? AppRealNameService.STATUS_NOT_SUBMITTED : status;
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
