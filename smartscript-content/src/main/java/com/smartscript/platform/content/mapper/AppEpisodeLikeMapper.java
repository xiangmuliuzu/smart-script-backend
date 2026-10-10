package com.smartscript.platform.content.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 剧集点赞 数据层（App 2.8.12）
 *
 * 依据：云端 script_platform_dev 库 sys_episode_like + sys_episode 表 + 接口文档 2.8.12。
 *
 * 反推处理点：
 * 1. sys_episode_like 有唯一键 uk_user_episode(user_id, episode_id)（已在真实库确认），
 *    故幂等新增直接用 INSERT IGNORE，重复点赞影响 0 行，不需要 WHERE NOT EXISTS。
 * 2. 计数维护：sys_episode.like_count 无触发器维护（information_schema.triggers 为空），
 *    由本层在「真正新增/删除」时显式自增、自减；自减用 GREATEST(...,0) 兜底，避免出现负数。
 * 3. likeCount 以 sys_episode.like_count 为权威回读，而非 count(sys_episode_like)：
 *    剧集可能存在历史计数，回读剧集列才能与详情页展示一致。
 *
 * @author xiangsipeng
 */
public interface AppEpisodeLikeMapper
{
    /**
     * 幂等点赞：已存在 (user_id, episode_id) 时不插入
     *
     * @param userId    点赞人ID（当前登录身份）
     * @param episodeId 剧集ID
     * @return 影响行数（1=新增成功，0=此前已点赞）
     */
    public int insertLikeIfAbsent(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    /**
     * 取消点赞（幂等：记录不存在时影响 0 行）
     *
     * @param userId    点赞人ID
     * @param episodeId 剧集ID
     * @return 影响行数
     */
    public int deleteLike(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    /**
     * 剧集点赞数自增（sys_episode.like_count + 1）
     *
     * @param episodeId 剧集ID
     * @return 影响行数
     */
    public int incrementEpisodeLikeCount(@Param("episodeId") Long episodeId);

    /**
     * 剧集点赞数自减（sys_episode.like_count - 1，下限 0）
     *
     * @param episodeId 剧集ID
     * @return 影响行数
     */
    public int decrementEpisodeLikeCount(@Param("episodeId") Long episodeId);

    /**
     * 读取剧集当前点赞数（权威值）
     *
     * @param episodeId 剧集ID
     * @return like_count；剧集不存在返回 null
     */
    public Integer selectEpisodeLikeCount(@Param("episodeId") Long episodeId);
}