package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppCommentInsert;
import com.smartscript.platform.content.dto.AppCommentItem;

/**
 * 剧集评论 数据层（App 2.8.10 列表 / 2.8.11 发表）
 *
 * 依据：云端 script_platform_dev 库 sys_comment 表 + 接口文档 2.8.10 / 2.8.11。
 *
 * 反推处理点：
 * 1. status 约定：1=正常（可见）、0=隐藏；列表只查 status = 1。
 * 2. 嵌套回复：列表按 episode_id 平铺下发（parent_id 原样带出），层级由前端按 parent_id 组，
 *    后端不做递归/裁剪，保证同一剧集的父子评论始终落在同一页。
 * 3. 计数维护：sys_comment / sys_episode 均无触发器（已在真实库确认 information_schema.triggers 为空），
 *    故 comment_count（剧集）与 reply_count（父评论）由本层在写入后显式自增，避免漏计。
 * 4. 表上无 (episode_id, created_at) 复合索引，仅有 idx_episode_id；排序按 comment_id 升序
 *    （自增主键天然等价于创建顺序，父评论必然先于其回复），避免额外排序开销。
 *
 * @author xiangsipeng
 */
public interface AppCommentMapper
{
    /**
     * 按剧集查询可见评论（平铺，含 parent_id；分页由调用方 PageHelper 驱动）
     *
     * @param episodeId 剧集ID
     * @return 评论集合（仅 status=1，按创建顺序升序）
     */
    public List<AppCommentItem> selectCommentsByEpisode(@Param("episodeId") Long episodeId);

    /**
     * 新增评论
     *
     * @param comment 评论入参（回填自增 commentId）
     * @return 影响行数
     */
    public int insertComment(AppCommentInsert comment);

    /**
     * 统计某条评论是否属于指定剧集（用于校验 parentId 与 episodeId 一致）
     *
     * @param parentId  父评论ID
     * @param episodeId 剧集ID
     * @return 记录数（1=同剧集，0=不存在或跨剧集）
     */
    public int countCommentInEpisode(@Param("parentId") Long parentId, @Param("episodeId") Long episodeId);

    /**
     * 剧集评论数自增（sys_episode.comment_count + 1）
     *
     * @param episodeId 剧集ID
     * @return 影响行数
     */
    public int incrementEpisodeCommentCount(@Param("episodeId") Long episodeId);

    /**
     * 父评论回复数自增（sys_comment.reply_count + 1）
     *
     * @param parentId 父评论ID
     * @return 影响行数
     */
    public int incrementParentReplyCount(@Param("parentId") Long parentId);
}