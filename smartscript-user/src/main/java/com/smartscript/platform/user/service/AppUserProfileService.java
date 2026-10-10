package com.smartscript.platform.user.service;

import java.time.LocalDateTime;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.constant.CreatorSpecialtyDictionary;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.domain.UserCreatorProfile;
import com.smartscript.platform.user.dto.CreatorProfileDto;
import com.smartscript.platform.user.dto.UserProfileDto;
import com.smartscript.platform.user.dto.UserProfileUpdateRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.mapper.AuthorCapabilityAdminMapper;
import com.smartscript.platform.user.util.AppHashes;

/**
 * A5 个人资料（契约 §1.2，规格 §8.3）。
 *
 * 允许修改昵称、头像与简介：
 *   - 手机号、角色、实名状态不在请求对象内，因此无法经此接口修改；
 *   - 目标用户始终取自当前登录身份（currentUserId），请求体不含 userId，
 *     不存在提交他人 userId 越权修改的可能；
 *   - 头像接受平台资源路径、兼容 http(s) 地址或空串（清空头像）；
 *   - 昵称去首尾空白并限制长度，空昵称拒绝（避免列表出现无名用户）；
 *   - 简介去首尾空白，允许空串（清空），长度上限 200。
 */
@Service
public class AppUserProfileService
{
    /** 昵称长度上限，与 sys_user.nick_name 列宽（30）一致。 */
    private static final int NICKNAME_MAX = 30;

    /** 头像地址长度上限，与 sys_user.avatar 列宽（100）一致。 */
    private static final int AVATAR_MAX = 100;

    /** 个人简介长度上限，与 A5 迁移（sys_user.bio VARCHAR(200)）一致。 */
    public static final int BIO_MAX = 200;

    /**
     * 注册时间的时区归属：数据库连接 serverTimezone=GMT+8（application-druid.yml），
     * sys_user.create_time 的墙钟值按 GMT+8 解释，输出 ISO 8601 带 +08:00 偏移。
     */
    private static final ZoneOffset DB_ZONE = ZoneOffset.of("+08:00");

    private static final DateTimeFormatter REGISTERED_AT_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AppUserMapper userMapper;
    private final AppUserCenterMapper centerMapper;
    private final AuthorCapabilityAdminMapper capabilityMapper;

    public AppUserProfileService(AppUserMapper userMapper, AppUserCenterMapper centerMapper,
            AuthorCapabilityAdminMapper capabilityMapper)
    {
        this.userMapper = userMapper;
        this.centerMapper = centerMapper;
        this.capabilityMapper = capabilityMapper;
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
        if (request == null || (request.getNickname() == null && request.getAvatar() == null && request.getBio() == null))
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
        if (request.getBio() != null)
        {
            String bio = normalizeBio(request.getBio());
            centerMapper.updateBio(userId, bio);
            user.setBio(bio);
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
     * 平台头像存资源路径，避免把上传设备的 localhost 等主机地址绑定到账号。
     * 历史平台绝对地址同样规范化；外部 HTTP(S) 图片地址继续兼容。
     */
    static String normalizeAvatar(String raw)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty())
        {
            return "";
        }
        if (!value.startsWith("/profile/"))
        {
            try
            {
                URI uri = URI.create(value.replace(" ", "%20"));
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null)
                {
                    throw new IllegalArgumentException();
                }
                String path = uri.getPath();
                int resourceStart = path == null ? -1 : path.indexOf("/profile/upload/");
                if (resourceStart < 0 && path != null)
                {
                    resourceStart = path.indexOf("/profile/avatar/");
                }
                if (resourceStart >= 0)
                {
                    value = path.substring(resourceStart);
                }
            }
            catch (IllegalArgumentException e)
            {
                throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "avatar must be a platform path or http(s) url");
            }
        }
        if (value.startsWith("/profile/") && !validAvatarPath(value))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "invalid avatar path");
        }
        if (value.length() > AVATAR_MAX)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "avatar too long");
        }
        return value;
    }

    private static boolean validAvatarPath(String value)
    {
        if (!(value.startsWith("/profile/upload/") || value.startsWith("/profile/avatar/"))
                || value.indexOf('\\') >= 0 || value.indexOf('?') >= 0 || value.indexOf('#') >= 0
                || value.indexOf('%') >= 0 || value.chars().anyMatch(Character::isISOControl)
                || !value.matches("(?i).*\\.(jpg|jpeg|png|gif|bmp)$"))
        {
            return false;
        }
        return Arrays.stream(value.substring(1).split("/", -1))
                .noneMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."));
    }

    /**
     * 简介规范化：去首尾空白；允许空串（清空）；上限 {@link #BIO_MAX}。
     * 与昵称不同，空简介合法（用户可随时清空）。
     */
    static String normalizeBio(String raw)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > BIO_MAX)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "bio too long");
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
        dto.setBio(emptyToNull(user.getBio()));
        dto.setPhoneMasked(AppHashes.maskPhone(user.getPhonenumber()));
        dto.setUserType(AppAuthenticationService.normalizeUserType(user.getUserType()));
        dto.setRealNameStatus(realNameStatus(user.getUserId()));
        // A4 增量：注册时间、账号状态原始代码与创作者资料投影
        dto.setRegisteredAt(formatRegisteredAt(user.getCreateTime()));
        dto.setAccountStatus(user.getStatus());
        dto.setCreatorProfile(creatorProfile(user.getUserId()));
        return dto;
    }

    /**
     * 注册时间输出：数据库墙钟值按 GMT+8 标注为 ISO 8601；
     * 历史缺失值返回 null（前端显示「—」），不拼接伪时区。
     */
    static String formatRegisteredAt(LocalDateTime createTime)
    {
        if (createTime == null)
        {
            return null;
        }
        return OffsetDateTime.of(createTime, DB_ZONE).format(REGISTERED_AT_FORMAT);
    }

    /**
     * 创作者资料投影：无作者能力返回 null；有能力时读 user_creator_profile，
     * 无资料行也返回对象本身（字段 null / 空列表，由前端显示「未填写」）。
     */
    private CreatorProfileDto creatorProfile(Long userId)
    {
        Boolean enabled = capabilityMapper.selectEnabledByUserId(userId);
        if (enabled == null || !enabled)
        {
            return null;
        }
        CreatorProfileDto dto = new CreatorProfileDto();
        UserCreatorProfile row = centerMapper.selectCreatorProfile(userId);
        if (row == null)
        {
            dto.setSpecialties(new ArrayList<>());
            return dto;
        }
        dto.setPenName(emptyToNull(row.getPenName()));
        dto.setSpecialties(specialties(row.getSpecialties()));
        dto.setIntroduction(emptyToNull(row.getProfileIntro()));
        return dto;
    }

    /**
     * 擅长类型代码解析为 {code, name}：代码按逗号/空白拆分、去空、去重；
     * 类型字典来源待项目确认，未登记代码的 name 回退为代码本身。
     */
    static List<CreatorProfileDto.Specialty> specialties(String raw)
    {
        List<CreatorProfileDto.Specialty> result = new ArrayList<>();
        if (raw == null || raw.isBlank())
        {
            return result;
        }
        Arrays.stream(raw.split("[,，\\s]+"))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .forEach(code -> result.add(new CreatorProfileDto.Specialty(code, CreatorSpecialtyDictionary.nameOf(code))));
        return result;
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
