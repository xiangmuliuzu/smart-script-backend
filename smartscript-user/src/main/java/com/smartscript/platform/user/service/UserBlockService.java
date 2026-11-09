package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.dto.UserSummaryDto;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.UserBlockMapper;
import com.smartscript.platform.user.mapper.UserFollowMapper;

/**
 * A5+ 用户个人黑名单：拉黑/解除拉黑，以及交易、聊天、关注链路的双向拦截。
 *
 * 强制边界：
 *   - 不能拉黑自己；目标必须存在且属于 App 账号域，否则 404。
 *   - 拉黑幂等（唯一键 + INSERT IGNORE），重复拉黑不产生重复记录。
 *   - 拦截按双向判断：任一方拉黑，双方都不能再建立新的互动。
 */
@Service
public class UserBlockService
{
    private final UserBlockMapper blockMapper;
    private final UserFollowMapper followMapper;

    public UserBlockService(UserBlockMapper blockMapper, UserFollowMapper followMapper)
    {
        this.blockMapper = blockMapper;
        this.followMapper = followMapper;
    }

    /** 拉黑（幂等）。返回是否发生变更。 */
    @Transactional
    public boolean block(Long userId, Long targetUserId)
    {
        if (targetUserId == null || targetUserId <= 0)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "用户不存在或暂不可用");
        }
        if (userId != null && userId.equals(targetUserId))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "不能拉黑自己");
        }
        if (followMapper.selectPublicUser(targetUserId) == null)
        {
            throw new AppAuthException(AppUserErrorCodes.RESOURCE_NOT_FOUND, 404,
                    AppUserErrorCodes.RESOURCE_NOT_FOUND_TEXT);
        }
        return blockMapper.insertBlock(userId, targetUserId) > 0;
    }

    /** 解除拉黑。返回是否发生变更。 */
    @Transactional
    public boolean unblock(Long userId, Long targetUserId)
    {
        if (targetUserId == null || targetUserId <= 0)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "用户不存在或暂不可用");
        }
        return blockMapper.deleteBlock(userId, targetUserId) > 0;
    }

    /** 我拉黑的用户列表。 */
    public List<UserSummaryDto> listBlocked(Long userId)
    {
        if (userId == null || userId <= 0)
        {
            return new ArrayList<>();
        }
        List<UserSummaryDto> rows = blockMapper.selectBlockedUsers(userId);
        return rows == null ? new ArrayList<>() : rows;
    }

    /** userId 是否拉黑了 targetUserId。 */
    public boolean hasBlocked(Long userId, Long targetUserId)
    {
        if (userId == null || targetUserId == null || userId <= 0 || targetUserId <= 0)
        {
            return false;
        }
        return blockMapper.countBlockRelation(userId, targetUserId) > 0;
    }

    /** 任一方拉黑另一方即为 true。 */
    public boolean blockedBetween(Long a, Long b)
    {
        return hasBlocked(a, b) || hasBlocked(b, a);
    }

    /**
     * 互动前置校验：actor 对 other 执行操作前，若任一方已拉黑则拒绝。
     *
     * @param action 操作名称（如「发起询盘」），用于双向提示文案。
     */
    public void requireNotBlocked(Long actorId, Long otherId, String action)
    {
        if (actorId == null || otherId == null || actorId.equals(otherId))
        {
            return;
        }
        if (hasBlocked(actorId, otherId))
        {
            throw new AppAuthException(AppUserErrorCodes.DOMAIN_OR_PERMISSION, 403,
                    "你已将对方加入黑名单，请先解除后再" + action);
        }
        if (hasBlocked(otherId, actorId))
        {
            throw new AppAuthException(AppUserErrorCodes.DOMAIN_OR_PERMISSION, 403,
                    "对方已将你加入黑名单，无法" + action);
        }
    }
}
