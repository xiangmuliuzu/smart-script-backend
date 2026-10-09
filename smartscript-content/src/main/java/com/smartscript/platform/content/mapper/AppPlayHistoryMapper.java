package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppPlayHistoryItem;

/**
 * 播放历史 数据层（App 2.8.6 播放历史）
 *
 * 依据：云端 script_platform_dev 库 sys_play_history + sys_episode + sys_work +
 * sys_play_progress 表 + 接口文档表 2-99。
 *
 * 反推处理点：
 * 1. 全部按当前用户查询，归属由服务层传入 userId（强条件）。
 * 2. 只回「已上架未删除」作品（LEFT JOIN 后 on_shelf 过滤），与 B 模块书城可见性口径一致；
 *    下架/删除后历史行不再展示。
 * 3. 续播位置来自 sys_play_progress（subquery 按 user_id + episode_id），
 *    播放历史行本身不含进度列（见 schema）。
 * 4. 分页由调用方 PageHelper 驱动。
 *
 * @author xiangsipeng
 */
public interface AppPlayHistoryMapper
{
    /**
     * 播放历史列表（按播放时间倒序；分页由调用方 PageHelper 驱动）
     *
     * @param userId 用户ID（当前登录身份）
     * @return 播放历史项
     */
    public List<AppPlayHistoryItem> selectPlayHistoryByUser(@Param("userId") Long userId);

    /**
     * 刷新某用户对某剧集的播放时间（历史行已存在时）
     *
     * 表上无 (user_id, episode_id) 唯一索引，同一剧集可能已有多行历史，
     * 故一次性把该用户该剧集的全部历史行刷新到当前时间，避免同一剧集在列表里重复出现。
     *
     * @param userId    用户ID
     * @param episodeId 剧集ID
     * @return 影响行数（0=无历史行，需改走 insertHistory）
     */
    public int touchHistory(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    /**
     * 新增播放历史（首次播放该剧集时）
     *
     * @param userId    用户ID
     * @param episodeId 剧集ID
     * @param workId    剧集所属作品ID
     * @return 影响行数
     */
    public int insertHistory(@Param("userId") Long userId, @Param("episodeId") Long episodeId,
            @Param("workId") Long workId);
}