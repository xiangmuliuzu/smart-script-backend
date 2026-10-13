package com.smartscript.platform.user.security;

import java.util.Set;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.security.context.AuthenticationContextHolder;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.framework.web.service.TokenService;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.util.AppHashes;

/** App account adapter over the shared RuoYi AuthenticationManager/TokenService. */
@Service
public class AppRuoYiSessionService
{
    public static final String DOMAIN = "app";
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final SysLoginService loginService;
    private final AppUserMapper userMapper;

    public AppRuoYiSessionService(TokenService tokenService, AuthenticationManager authenticationManager,
            SysLoginService loginService, AppUserMapper userMapper)
    {
        this.tokenService = tokenService;
        this.authenticationManager = authenticationManager;
        this.loginService = loginService;
        this.userMapper = userMapper;
    }

    public void authenticate(AppUserRecord user, String password, String code, String uuid)
    {
        requireAppAccount(user);
        if (password == null || password.length() < com.ruoyi.common.constant.UserConstants.PASSWORD_MIN_LENGTH || password.length() > 64
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "用户不存在/密码错误");
        }
        var credentials = new UsernamePasswordAuthenticationToken(user.getUserName(), password);
        try
        {
            loginService.validateCaptcha(user.getUserName(), code, uuid);
            loginService.validateLoginIp(user.getUserName());
            AuthenticationContextHolder.setContext(credentials);
            var result = authenticationManager.authenticate(credentials);
            if (!(result.getPrincipal() instanceof LoginUser loginUser)
                    || !DOMAIN.equals(loginUser.getCredentialDomain())
                    || !user.getUserId().equals(loginUser.getUserId()))
            {
                throw new AppAuthException(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, 403, "credential domain mismatch");
            }
        }
        catch (AppAuthException e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            if (!(e instanceof org.springframework.security.core.AuthenticationException)
                    && !(e instanceof com.ruoyi.common.exception.user.UserException)
                    && !(e instanceof com.ruoyi.common.exception.ServiceException))
            {
                throw e;
            }
            // Keep RuoYi retry-limit/captcha messages, never include credentials.
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400,
                    e instanceof org.springframework.security.authentication.BadCredentialsException
                            ? "用户不存在/密码错误" : e.getMessage());
        }
        finally
        {
            AuthenticationContextHolder.clearContext();
        }
    }

    public String issue(AppUserRecord user)
    {
        requireAppAccount(user);
        try
        {
            loginService.validateLoginIp(user.getUserName());
        }
        catch (com.ruoyi.common.exception.user.UserException e)
        {
            throw new AppAuthException(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, 403, e.getMessage());
        }
        SysUser account = new SysUser();
        account.setUserId(user.getUserId());
        account.setUserName(user.getUserName());
        account.setNickName(user.getNickName());
        account.setUserType(user.getUserType());
        account.setStatus(user.getStatus());
        account.setDelFlag(user.getDelFlag());
        LoginUser loginUser = new LoginUser(user.getUserId(), null, account, Set.of());
        loginUser.setCredentialDomain(DOMAIN);
        return tokenService.createToken(loginUser);
    }

    public AppIdentityContext parseAndValidate(String token)
    {
        LoginUser session = tokenService.getLoginUser(token);
        if (session == null)
        {
            throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "login expired, please sign in again");
        }
        if (!DOMAIN.equals(session.getCredentialDomain()))
        {
            throw new AppAuthException(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, 403, "credential domain mismatch");
        }
        AppUserRecord user = userMapper.selectById(session.getUserId());
        requireAppAccount(user);
        tokenService.verifyToken(session);
        String realNameStatus = userMapper.selectRealNameStatus(user.getUserId());
        return new AppIdentityContext(user.getUserId(), user.getUserType(), user.getStatus(),
                AppHashes.maskPhone(user.getPhonenumber()), user.getNickName(), user.getAvatar(),
                realNameStatus == null ? "NOT_SUBMITTED" : realNameStatus, null, session.getToken(), null);
    }

    private static void requireAppAccount(AppUserRecord user)
    {
        if (user == null || !user.isUsable())
        {
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "account disabled");
        }
        if (user.getUserType() == null || !Set.of("01", "02", "03").contains(user.getUserType()))
        {
            throw new AppAuthException(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, 403, "account is not an App user");
        }
    }
}
