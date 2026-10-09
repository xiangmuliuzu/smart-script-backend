package com.smartscript.platform.content.dto;

/**
 * 剧集点赞结果（B 模块 2.8.12 表2-105）。
 *
 * data 载荷：{liked, likeCount}。
 * liked 表示本次操作后的点赞态（POST 恒为 true，DELETE 恒为 false）；
 * likeCount 为操作完成后 sys_episode.like_count 的权威值，供前端直接回显，无需再查。
 *
 * @author xiangsipeng
 */
public class AppEpisodeLikeResult
{
    /** 操作后的点赞态 */
    private boolean liked;

    /** 操作后剧集点赞数（sys_episode.like_count） */
    private int likeCount;

    public AppEpisodeLikeResult()
    {
    }

    public AppEpisodeLikeResult(boolean liked, int likeCount)
    {
        this.liked = liked;
        this.likeCount = likeCount;
    }

    public boolean isLiked()
    {
        return liked;
    }

    public void setLiked(boolean liked)
    {
        this.liked = liked;
    }

    public int getLikeCount()
    {
        return likeCount;
    }

    public void setLikeCount(int likeCount)
    {
        this.likeCount = likeCount;
    }
}