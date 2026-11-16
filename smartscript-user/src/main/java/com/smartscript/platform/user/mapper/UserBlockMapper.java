package com.smartscript.platform.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.dto.UserSummaryDto;

/**
 * A5+ 用户个人黑名单关系。
 *
 * 强制边界：
 *   - 关系唯一键 (user_id, blocked_user_id) 由数据库强制，重复拉黑幂等（INSERT IGNORE）。
 *   - 拉黑自己由服务层拒绝，不入库。
 *   - 是否拉黑只按发起方向判断；双向拦截由服务层组合两次查询。
 */
public interface UserBlockMapper
{
    /** 建立拉黑关系（幂等）；重复拉黑影响 0 行。 */
    int insertBlock(@Param("userId") Long userId, @Param("blockedUserId") Long blockedUserId);

    /** 解除拉黑；未拉黑影响 0 行。 */
    int deleteBlock(@Param("userId") Long userId, @Param("blockedUserId") Long blockedUserId);

    /** userId 是否拉黑了 blockedUserId。 */
    int countBlockRelation(@Param("userId") Long userId, @Param("blockedUserId") Long blockedUserId);

    /** 我拉黑的用户列表（公开字段，按拉黑时间倒序）。 */
    List<UserSummaryDto> selectBlockedUsers(@Param("userId") Long userId);
}
