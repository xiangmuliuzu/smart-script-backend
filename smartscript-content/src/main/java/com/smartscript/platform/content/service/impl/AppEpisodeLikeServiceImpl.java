package com.smartscript.platform.content.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.content.dto.AppEpisodeLikeResult;
import com.smartscript.platform.content.mapper.AppEpisodeLikeMapper;
import com.smartscript.platform.content.service.IAppEpisodeLikeService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 剧集点赞 服务实现（App 2.8.12）
 *
 * 依据：接口文档 2.8.12 + 云端 script_platform_dev 库 sys_episode_like / sys_episode 表。
 *
 * 反推处理点：
 * 1. 幂等：点赞走 INSERT IGNORE（依赖唯一键 uk_user_episode），取消走 DELETE；
 *    仅当影响行数 &gt;0（真正新增/删除）时才调整 sys_episode.like_count，避免重复操作把计数刷坏。
 * 2. likeCount 回读 sys_episode.like_count 作为权威值下发，供前端直接回显。
 * 3. 身份不做空值兜底：两段路径未命中公开白名单，过滤器已保证非游客。
 *
 * @author xiangsipeng
 */
@Service
public class AppEpisodeLikeServiceImpl implements IAppEpisodeLikeService
{
    /** 是否已点赞（POST 恒为 true，DELETE 恒为 false） */
    private static final boolean LIKED = true;

    private static final boolean UNLIKED = false;

    @Autowired
    private AppEpisodeLikeMapper likeMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    @Transactional
    public AppEpisodeLikeResult like(Long episodeId)
    {
        Long userId = identityProvider.currentUserId();
        if (likeMapper.insertLikeIfAbsent(userId, episodeId) > 0)
        {
            likeMapper.incrementEpisodeLikeCount(episodeId);
        }
        return new AppEpisodeLikeResult(LIKED, currentLikeCount(episodeId));
    }

    @Override
    @Transactional
    public AppEpisodeLikeResult unlike(Long episodeId)
    {
        Long userId = identityProvider.currentUserId();
        if (likeMapper.deleteLike(userId, episodeId) > 0)
        {
            likeMapper.decrementEpisodeLikeCount(episodeId);
        }
        return new AppEpisodeLikeResult(UNLIKED, currentLikeCount(episodeId));
    }

    /** 读取剧集最新点赞数（剧集存在性由控制层先行校验，此处 null 兜底为 0） */
    private int currentLikeCount(Long episodeId)
    {
        Integer count = likeMapper.selectEpisodeLikeCount(episodeId);
        return count == null ? 0 : count;
    }
}