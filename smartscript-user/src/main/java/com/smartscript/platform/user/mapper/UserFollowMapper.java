package com.smartscript.platform.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.UserSummaryDto;

/**
 * A5 用户社交：关注/粉丝关系与公开用户查询。
 *
 * 强制边界：
 *   - 关注关系唯一键 (follower_id, followee_id) 由数据库强制，重复关注幂等（INSERT IGNORE）。
 *   - 关注自己由服务层拒绝，不入库。
 *   - 列表只返回公开字段；「当前用户是否已关注」由 SQL 关联计算，不在应用层 N+1。
 */
public interface UserFollowMapper
{
    /** 建立关注关系（幂等）；重复关注影响 0 行。 */
    int insertFollow(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /** 解除关注；未关注影响 0 行。 */
    int deleteFollow(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /** 指定用户关注的其他人数量。 */
    int countFollowing(@Param("userId") Long userId);

    /** 指定用户的粉丝数量。 */
    int countFollowers(@Param("userId") Long userId);

    /** 是否已关注。 */
    int countFollowRelation(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /** 可分页的公开用户查询：按用户 id 取公开资料。不存在返回 null。 */
    AppUserRecord selectPublicUser(@Param("userId") Long userId);

    /**
     * 关注列表（我关注的人）。
     * viewerId 为当前登录用户，用于计算每行 isFollowing。
     */
    List<UserSummaryDto> selectFollowing(@Param("userId") Long userId, @Param("viewerId") Long viewerId);

    /** 粉丝列表（关注我的人）。 */
    List<UserSummaryDto> selectFollowers(@Param("userId") Long userId, @Param("viewerId") Long viewerId);
}
