package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppEpisodeLikeResult;

/**
 * 剧集点赞 服务层（App 2.8.12）
 *
 * 依据：接口文档 2.8.12 + 云端 script_platform_dev 库 sys_episode_like / sys_episode 表。
 *
 * 边界：App 私有接口，归属取 {@code IdentityProvider.currentUserId()}；
 * 幂等——重复点赞/取消均返回当前态与最新点赞数，不报错（前端可能连点）。
 *
 * @author xiangsipeng
 */
public interface IAppEpisodeLikeService
{
    /**
     * 点赞（2.8.12，幂等）
     *
     * @param episodeId 剧集ID
     * @return 操作后点赞态（liked=true）与剧集最新点赞数
     */
    public AppEpisodeLikeResult like(Long episodeId);

    /**
     * 取消点赞（2.8.12，幂等）
     *
     * @param episodeId 剧集ID
     * @return 操作后点赞态（liked=false）与剧集最新点赞数
     */
    public AppEpisodeLikeResult unlike(Long episodeId);
}