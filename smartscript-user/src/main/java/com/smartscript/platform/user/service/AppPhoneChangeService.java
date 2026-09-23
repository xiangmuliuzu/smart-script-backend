package com.smartscript.platform.user.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.domain.UserPhoneChangeLog;
import com.smartscript.platform.user.dto.PhoneChangeConfirmRequest;
import com.smartscript.platform.user.dto.PhoneChangeNewPhoneRequest;
import com.smartscript.platform.user.dto.PhoneChangeVerifyRequest;
import com.smartscript.platform.user.dto.PhoneStepUpResult;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.security.AppIdentityContext;
import com.smartscript.platform.user.util.AppHashes;

/**
 * A5 账号安全——换绑手机号（契约 §1.4，规格 §8.5）。
 *
 * 三步流程，两步凭证：
 *   1. old/verify   验证旧号（CHANGE_PHONE_OLD 验证码）-> 签发一次性 stepUpToken
 *   2. new/send     向新号发码（CHANGE_PHONE_NEW 验证码）
 *   3. confirm      同时校验 stepUpToken 与新号验证码 -> 单事务改号
 *
 * 强制边界：
 *   - 两步凭证都短时、一次性，且绑定用户与设备（换绑他人凭证或换设备不可用）。
 *   - 新手机号全局唯一：先查后改，最终由 uk_sys_user_phonenumber 兜底并发。
 *   - 换绑成功后吊销该用户全部 App 会话，客户端须用新号重新登录。
 *   - 审计只写掩码；验证码与凭证不落库、不进日志。
 */
@Service
public class AppPhoneChangeService
{
    private static final Logger log = LoggerFactory.getLogger(AppPhoneChangeService.class);

    /** 场景常量与 app_sms_code.scene 取值一致（A2 已含这两个场景）。 */
    static final String SCENE_CHANGE_PHONE_OLD = "CHANGE_PHONE_OLD";
    static final String SCENE_CHANGE_PHONE_NEW = "CHANGE_PHONE_NEW";

    /** 换绑审计结果标记。 */
    private static final String RESULT_SUCCESS = "SUCCESS";

    private final AppUserMapper userMapper;
    private final AppUserCenterMapper centerMapper;
    private final SmsCodeService smsCodeService;
    private final PhoneChangeTokenStore tokenStore;
    private final AppSessionRevocationService revocationService;

    public AppPhoneChangeService(AppUserMapper userMapper,
            AppUserCenterMapper centerMapper,
            SmsCodeService smsCodeService,
            PhoneChangeTokenStore tokenStore,
            AppSessionRevocationService revocationService)
    {
        this.userMapper = userMapper;
        this.centerMapper = centerMapper;
        this.smsCodeService = smsCodeService;
        this.tokenStore = tokenStore;
        this.revocationService = revocationService;
    }

    /**
     * 第 1 步（a）：向当前登录用户自己的手机号发送换绑验证码。
     *
     * 为什么需要这个专用端点而不是复用 `POST /api/v1/auth/sms/send`：
     * 该公开发码接口要求请求体提供完整手机号，而 App 侧只持有脱敏号，
     * 让它提供完整旧号会把「当前号码」暴露成客户端输入；
     * 这里号码由服务端按当前身份取，客户端无从指定发给谁。
     */
    public Map<String, Object> sendOldPhoneCode(AppIdentityContext identity, String ip, String requestId)
    {
        AppUserRecord user = requireUser(identity.getUserId());
        smsCodeService.sendCode(user.getPhonenumber(), SCENE_CHANGE_PHONE_OLD, ip, requestId);
        return Map.of(
                "sent", true,
                "phoneMasked", AppHashes.maskPhone(user.getPhonenumber()));
    }

    /**
     * 第 1 步：校验旧号验证码，签发 step-up 凭证。
     *
     * 验证码由 {@link #sendOldPhoneCode} 发到当前用户自己的号码；
     * 服务端不接受请求体指定场景，避免把该端点变成任意场景的验证码校验器。
     */
    public PhoneStepUpResult verifyOldPhone(AppIdentityContext identity, PhoneChangeVerifyRequest request,
            String ip, String requestId)
    {
        if (request == null || request.getCode() == null || request.getCode().isBlank())
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "code required");
        }
        AppUserRecord user = requireUser(identity.getUserId());
        smsCodeService.consumeOnce(user.getPhonenumber(), SCENE_CHANGE_PHONE_OLD, request.getCode());
        String stepUpToken = tokenStore.issue(user.getUserId(), request.getDeviceId());
        return new PhoneStepUpResult(stepUpToken, PhoneChangeTokenStore.TTL_SECONDS);
    }

    /**
     * 第 2 步：向新号发码。
     *
     * 发码前先判定新号是否已被占用，让用户尽早得到确定性结论，
     * 而不是收到验证码、填完表单后才在最后一步失败。
     */
    public Map<String, Object> sendNewPhoneCode(AppIdentityContext identity, PhoneChangeNewPhoneRequest request,
            String ip, String requestId)
    {
        if (request == null || request.getNewPhone() == null || request.getNewPhone().isBlank())
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "new phone required");
        }
        AppUserRecord user = requireUser(identity.getUserId());
        String newPhone = request.getNewPhone().trim();
        if (newPhone.equals(user.getPhonenumber()))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "new phone equals current phone");
        }
        if (centerMapper.countPhoneTaken(newPhone, user.getUserId()) > 0)
        {
            throw new AppAuthException(AppUserErrorCodes.PHONE_TAKEN, 409, "phone already registered");
        }
        smsCodeService.sendCode(newPhone, SCENE_CHANGE_PHONE_NEW, ip, requestId);
        return Map.of("sent", true);
    }

    /**
     * 第 3 步：确认换绑。
     *
     * 顺序固定为先消费 step-up 凭证、再校验新号验证码，最后改号：
     * 凭证的消费是「本人持旧号」的证明，必须在任何写操作之前确立。
     */
    @Transactional
    public Map<String, Object> confirm(AppIdentityContext identity, PhoneChangeConfirmRequest request, String ip)
    {
        if (request == null || request.getNewPhone() == null || request.getNewPhone().isBlank()
                || request.getCode() == null || request.getCode().isBlank())
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "payload incomplete");
        }
        Long userId = identity.getUserId();
        AppUserRecord user = requireUser(userId);
        String newPhone = request.getNewPhone().trim();

        if (!tokenStore.consume(request.getStepUpToken(), userId, request.getDeviceId()))
        {
            // 凭证不存在、已消费、过期或绑定不符：统一按不可用处理，不区分原因以免探测
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "step-up verification required");
        }
        if (newPhone.equals(user.getPhonenumber()))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "new phone equals current phone");
        }
        // 先于写操作判定占用；并发由唯一索引兜底（下方捕获）
        if (centerMapper.countPhoneTaken(newPhone, userId) > 0)
        {
            throw new AppAuthException(AppUserErrorCodes.PHONE_TAKEN, 409, "phone already registered");
        }
        smsCodeService.consumeOnce(newPhone, SCENE_CHANGE_PHONE_NEW, request.getCode());

        String oldPhone = user.getPhonenumber();
        try
        {
            centerMapper.updatePhone(userId, newPhone);
        }
        catch (DataIntegrityViolationException e)
        {
            // 并发换绑到同一号：唯一索引拒绝，对外仍是「号已占用」
            throw new AppAuthException(AppUserErrorCodes.PHONE_TAKEN, 409, "phone already registered");
        }

        UserPhoneChangeLog changeLog = new UserPhoneChangeLog();
        changeLog.setUserId(userId);
        changeLog.setOldPhoneMask(AppHashes.maskPhone(oldPhone));
        changeLog.setNewPhoneMask(AppHashes.maskPhone(newPhone));
        changeLog.setResult(RESULT_SUCCESS);
        changeLog.setClientIp(ip);
        changeLog.setDeviceId(request.getDeviceId());
        centerMapper.insertPhoneChangeLog(changeLog);

        // 换绑后旧会话一律失效：客户端须用新号重新登录
        revocationService.revokeAllForUser(userId, "PHONE_CHANGE");
        log.info("a5-phone change completed userId={}", userId);
        return Map.of("changed", true, "phoneMasked", AppHashes.maskPhone(newPhone));
    }

    private AppUserRecord requireUser(Long userId)
    {
        AppUserRecord user = userMapper.selectById(userId);
        if (user == null || !user.isUsable())
        {
            throw new AppAuthException(com.smartscript.platform.user.constant.AppAuthErrorCodes.ACCOUNT_DISABLED,
                    403, "account disabled");
        }
        return user;
    }
}
