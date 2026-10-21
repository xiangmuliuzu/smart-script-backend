package com.smartscript.platform.user.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.smartscript.platform.identity.WorkDeletionVerification;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.util.AppHashes;

/** 只向服务端取得的当前绑定手机号发送删除验证码，复用短信频控与一次性消费。 */
@Service
public class AppWorkDeletionVerification implements WorkDeletionVerification
{
    static final String SCENE = "DELETE_WORK";

    private final AppUserMapper userMapper;
    private final SmsCodeService smsCodeService;
    private final AppAuthProperties properties;

    public AppWorkDeletionVerification(AppUserMapper userMapper, SmsCodeService smsCodeService,
            AppAuthProperties properties)
    {
        this.userMapper = userMapper;
        this.smsCodeService = smsCodeService;
        this.properties = properties;
    }

    @Override
    public Map<String, Object> sendCode(Long userId, String ip, String requestId)
    {
        String phone = requirePhone(userId);
        try
        {
            smsCodeService.sendCode(phone, SCENE, ip, requestId);
        }
        catch (AppAuthException e)
        {
            throw translate(e);
        }
        return Map.of("sent", true, "phoneMasked", AppHashes.maskPhone(phone),
                "cooldownSeconds", properties.getSms().getCooldownSeconds());
    }

    @Override
    public void consumeCode(Long userId, String code)
    {
        String phone = requirePhone(userId);
        if (code == null || !code.matches("\\d{4,8}"))
        {
            throw new ServiceException("请输入正确格式的短信验证码", AppAuthErrorCodes.PARAM);
        }
        try
        {
            smsCodeService.consumeOnce(phone, SCENE, code);
        }
        catch (AppAuthException e)
        {
            throw translate(e);
        }
    }

    private String requirePhone(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录", AppAuthErrorCodes.UNAUTHORIZED);
        }
        AppUserRecord user = userMapper.selectById(userId);
        if (user == null || !user.isUsable())
        {
            throw new ServiceException("账号不可用", AppAuthErrorCodes.ACCOUNT_DISABLED);
        }
        String phone = user.getPhonenumber();
        if (phone == null || !phone.matches("^1\\d{10}$"))
        {
            throw new ServiceException("请先绑定手机号后再删除作品", AppAuthErrorCodes.PARAM);
        }
        return phone;
    }

    private static ServiceException translate(AppAuthException e)
    {
        String message = switch (e.getCode())
        {
            case AppAuthErrorCodes.SMS_COOLDOWN -> "发送过于频繁，请稍后重新获取验证码";
            case AppAuthErrorCodes.SMS_RATE_LIMIT -> "验证码发送次数已达上限，请稍后重试";
            default -> e.getMessage();
        };
        return new ServiceException(message, e.getCode());
    }
}
