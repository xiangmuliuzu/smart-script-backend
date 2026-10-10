package com.ruoyi.web.controller.pc;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.LoginBody;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.SysLoginService;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.AuthSessionDto;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.service.AppAuthenticationService;

/**
 * A 模块：PC 统一登录入口。管理员（00）与 App 用户（01/02/03）共用同一登录页，
 * 由服务端依据 sys_user.user_type 决定账号域与令牌体系：
 *
 *   - 00：走若依 SysLoginService，签发管理端 Token（response 带 token）。
 *   - 01/02/03：走 AppAuthenticationService.pcPasswordLogin，签发 App 凭证域
 *     access/refresh Token（response 带 session），与 App 端同一套令牌，绝不混用。
 *
 * 身份判定只依据服务端 user_type，不依赖用户名/手机号格式或前端名单。
 * 放在 ruoyi-admin 而非 smartscript-user：本接口横跨两个凭证域，
 * smartscript-user 的模块装配测试要求其内仅允许 App 域控制器。
 * 本接口挂在 App 凭证域过滤链（见 AppAuthSecurityConfig），仅 POST /login 公开。
 */
@RestController
@RequestMapping("/api/v1/pc-auth")
public class PcUnifiedLoginController
{
    private final AppUserMapper userMapper;
    private final SysLoginService loginService;
    private final AppAuthenticationService appAuthenticationService;

    public PcUnifiedLoginController(AppUserMapper userMapper,
            SysLoginService loginService,
            AppAuthenticationService appAuthenticationService)
    {
        this.userMapper = userMapper;
        this.loginService = loginService;
        this.appAuthenticationService = appAuthenticationService;
    }

    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody body, HttpServletRequest http)
    {
        String identifier = StringUtils.trimToEmpty(body.getUsername());
        String password = body.getPassword();
        if (StringUtils.isEmpty(identifier) || StringUtils.isEmpty(password))
        {
            return AjaxResult.error("用户不存在/密码错误");
        }

        AppUserRecord account = userMapper.selectByLoginIdentifier(identifier);
        if (account == null || AppAdminConstants.USER_TYPE_PC_ADMIN.equals(account.getUserType()))
        {
            // 管理域（含账号不存在——统一走 /login 的失败文案与审计，防账号枚举）。
            // 若 account 按手机号命中，则换回 userName 交给若依按用户名认证。
            String loginName = account != null ? account.getUserName() : identifier;
            String token = loginService.login(loginName, password, body.getCode(), body.getUuid());
            AjaxResult ajax = AjaxResult.success();
            ajax.put("accountType", AppAdminConstants.USER_TYPE_PC_ADMIN);
            ajax.put("token", token);
            return ajax;
        }

        if (AppAdminConstants.isManagedUserType(account.getUserType()))
        {
            // App 用户域：验证码与 App 端登录共用若依验证码体系（config 关闭时跳过）。
            loginService.validateCaptcha(identifier, body.getCode(), body.getUuid());
            try
            {
                AuthSessionDto session = appAuthenticationService.pcPasswordLogin(identifier, password, clientIp(http));
                AjaxResult ajax = AjaxResult.success();
                ajax.put("accountType", account.getUserType());
                ajax.put("session", session);
                return ajax;
            }
            catch (AppAuthException e)
            {
                return AjaxResult.error(e.getMessage());
            }
        }

        return AjaxResult.error("用户不存在/密码错误");
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
}
