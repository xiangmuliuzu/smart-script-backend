package com.smartscript.platform.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.UserProfileDto;
import com.smartscript.platform.user.dto.UserProfileUpdateRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.util.AppHashes;

/**
 * A5 个人资料（契约 §1.2，规格 §8.3）。
 *
 * 只允许修改昵称与头像：
 *   - 手机号、角色、实名状态不在请求对象内，因此无法经此接口修改；
 *   - 头像只接受平台上传服务返回的 http(s) 地址或空串（清空头像）；
 *   - 昵称去首尾空白并限制长度，空昵称拒绝（避免列表出现无名用户）。
 */
@Service
public class AppUserProfileService
{
    /** 昵称长度上限，与 sys_user.nick_name 列宽（30）一致。 */
    private static final int NICKNAME_MAX = 30;

    /** 头像地址长度上限，与 sys_user.avatar 列宽（100）一致。 */
    private static final int AVATAR_MAX = 100;

    private final AppUserMapper userMapper;
    private final AppUserCenterMapper centerMapper;

    public AppUserProfileService(AppUserMapper userMapper, AppUserCenterMapper centerMapper)
    {
        this.userMapper = userMapper;
        this.centerMapper = centerMapper;
    }

    public UserProfileDto getProfile(Long userId)
    {
        return toProfile(requireUser(userId));
    }

    /**
     * 更新资料。字段缺省表示不修改；两个字段都缺省视为无效请求。
     * 返回更新后的完整资料，供客户端直接刷新全局 currentUser。
     */
    @Transactional
    public UserProfileDto updateProfile(Long userId, UserProfileUpdateRequest request)
    {
        if (request == null || (request.getNickname() == null && request.getAvatar() == null))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "no updatable field");
        }
        AppUserRecord user = requireUser(userId);
        if (request.getNickname() != null)
        {
            String nickName = normalizeNickname(request.getNickname());
            centerMapper.updateNickName(userId, nickName);
            user.setNickName(nickName);
        }
        if (request.getAvatar() != null)
        {
            String avatar = normalizeAvatar(request.getAvatar());
            centerMapper.updateAvatar(userId, avatar);
            user.setAvatar(avatar);
        }
        return toProfile(user);
    }

    static String normalizeNickname(String raw)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty())
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "nickname required");
        }
        if (value.length() > NICKNAME_MAX)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "nickname too long");
        }
        return value;
    }

    /**
     * 头像规范化：允许空串（清空）或 http(s) 地址。
     *
     * 只接受绝对 http(s) 地址，拒绝 javascript:/data: 等可执行或内联协议，
     * 避免客户端把用户可控内容当资源加载。
     */
    static String normalizeAvatar(String raw)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty())
        {
            return "";
        }
        if (value.length() > AVATAR_MAX)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "avatar too long");
        }
        String lower = value.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://"))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "avatar must be an http(s) url");
        }
        return value;
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

    private UserProfileDto toProfile(AppUserRecord user)
    {
        UserProfileDto dto = new UserProfileDto();
        dto.setUserId(user.getUserId());
        dto.setNickname(user.getNickName());
        dto.setAvatar(emptyToNull(user.getAvatar()));
        dto.setPhoneMasked(AppHashes.maskPhone(user.getPhonenumber()));
        dto.setUserType(AppAuthenticationService.normalizeUserType(user.getUserType()));
        dto.setRealNameStatus(realNameStatus(user.getUserId()));
        return dto;
    }

    private String realNameStatus(Long userId)
    {
        String status = centerMapper.selectLatestRealNameStatus(userId);
        return status == null || status.isBlank() ? AppRealNameService.STATUS_NOT_SUBMITTED : status;
    }

    private static String emptyToNull(String value)
    {
        return value == null || value.isBlank() ? null : value;
    }
}
