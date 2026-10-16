package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.UserPublicProfileDto;
import com.smartscript.platform.user.dto.UserSummaryDto;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.UserFollowMapper;

/**
 * A5 用户社交：个人主页公开资料、关注/取关、关注/粉丝列表。
 *
 * 强制边界：
 *   - 公开资料只含公开字段，不含手机号、实名状态、账号状态。
 *   - 关注/粉丝数与 isFollowing 均来自真实关注关系（sys_user_follow），不写死、不造数。
 *   - 不能关注自己；目标必须存在且属于 App 账号域，否则 404。
 *   - 关注幂等（唯一键 + INSERT IGNORE），重复关注不产生重复记录。
 */
@Service
public class UserSocialService
{
    private final UserFollowMapper followMapper;
    private final AppUserProfileService profileService;

    public UserSocialService(UserFollowMapper followMapper, AppUserProfileService profileService)
    {
        this.followMapper = followMapper;
        this.profileService = profileService;
    }

    /** 指定用户的公开资料（个人主页）；不存在或非 App 账号域返回 404。 */
    public UserPublicProfileDto publicProfile(Long viewerId, Long targetUserId)
    {
        if (targetUserId == null || targetUserId <= 0)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "invalid user");
        }
        AppUserRecord user = followMapper.selectPublicUser(targetUserId);
        if (user == null)
        {
            throw new AppAuthException(AppUserErrorCodes.RESOURCE_NOT_FOUND, 404,
                    AppUserErrorCodes.RESOURCE_NOT_FOUND_TEXT);
        }
        boolean self = viewerId != null && viewerId.equals(targetUserId);
        UserPublicProfileDto dto = new UserPublicProfileDto();
        dto.setUserId(user.getUserId());
        dto.setNickname(user.getNickName());
        dto.setAvatar(emptyToNull(user.getAvatar()));
        dto.setBio(emptyToNull(user.getBio()));
        dto.setUserType(user.getUserType());
        dto.setRegisteredAt(AppUserProfileService.formatRegisteredAt(user.getCreateTime()));
        dto.setFollowingCount(followMapper.countFollowing(targetUserId));
        dto.setFollowerCount(followMapper.countFollowers(targetUserId));
        dto.setSelf(self);
        dto.setFollowing(!self && viewerId != null
                && followMapper.countFollowRelation(viewerId, targetUserId) > 0);
        dto.setCreatorProfile(profileService.creatorProfile(targetUserId));
        return dto;
    }

    /** 关注（幂等）。返回是否发生变更。 */
    @Transactional
    public boolean follow(Long followerId, Long followeeId)
    {
        requireFollowable(followerId, followeeId);
        return followMapper.insertFollow(followerId, followeeId) > 0;
    }

    /** 取关。返回是否发生变更。 */
    @Transactional
    public boolean unfollow(Long followerId, Long followeeId)
    {
        if (followeeId == null || followeeId <= 0)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "invalid user");
        }
        return followMapper.deleteFollow(followerId, followeeId) > 0;
    }

    /** 我关注的人。 */
    public List<UserSummaryDto> following(Long userId, Long viewerId)
    {
        List<UserSummaryDto> rows = followMapper.selectFollowing(userId, viewerId);
        return rows == null ? new ArrayList<>() : rows;
    }

    /** 关注我的人。 */
    public List<UserSummaryDto> followers(Long userId, Long viewerId)
    {
        List<UserSummaryDto> rows = followMapper.selectFollowers(userId, viewerId);
        return rows == null ? new ArrayList<>() : rows;
    }

    private void requireFollowable(Long followerId, Long followeeId)
    {
        if (followeeId == null || followeeId <= 0)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "invalid user");
        }
        if (followerId != null && followerId.equals(followeeId))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "不能关注自己");
        }
        if (followMapper.selectPublicUser(followeeId) == null)
        {
            throw new AppAuthException(AppUserErrorCodes.RESOURCE_NOT_FOUND, 404,
                    AppUserErrorCodes.RESOURCE_NOT_FOUND_TEXT);
        }
    }

    private static String emptyToNull(String value)
    {
        return value == null || value.isBlank() ? null : value;
    }
}
