package com.smartscript.platform.content.service;

import org.springframework.stereotype.Service;
import com.smartscript.platform.content.mapper.AppEpisodeLikeMapper;

/**
 * 个人主页内容统计（B 模块书城侧）。
 *
 * 只负责与「作品」相关的公开统计：作者累计获赞数（其作品下所有剧集 like_count 之和）。
 * 作者的上架作品列表复用书城分页（{@code /api/v1/content/works?authorId=}），不另设端点。
 */
@Service
public class AppProfileContentService
{
    private final AppEpisodeLikeMapper likeMapper;

    public AppProfileContentService(AppEpisodeLikeMapper likeMapper)
    {
        this.likeMapper = likeMapper;
    }

    /** 作者累计获赞数：其作品下所有剧集 like_count 之和（非用户自己点赞数）。 */
    public int likeCount(Long authorId)
    {
        if (authorId == null || authorId <= 0)
        {
            return 0;
        }
        return likeMapper.selectAuthorLikeCount(authorId);
    }
}
