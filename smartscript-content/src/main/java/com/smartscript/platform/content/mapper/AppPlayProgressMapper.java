package com.smartscript.platform.content.mapper;

import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppPlayProgressDto;

/**
 * 播放进度 数据层（App 2.8.4 保存播放进度 / 2.8.5 获取播放进度）
 *
 * 依据：云端 script_platform_dev 库 sys_play_progress 表 + 接口文档表 2-97 / 2-98。
 *
 * 反推处理点：
 * 1. 表上有唯一索引 uk_user_episode(user_id, episode_id)（实测 non_unique=0），
 *    故一个用户对同一剧集只保留一行进度；写入采用「先更后插」：
 *    updateProgress 影响 0 行时再 insertProgress（由唯一键兜底避免重复行）。
 * 2. work_id 为 NOT NULL，插入时必须显式提供（由服务层用剧集所属 work_id 传入）。
 * 3. updated_at 有 DEFAULT CURRENT_TIMESTAMP ON UPDATE，仍显式写 now()，不依赖库默认值差异。
 * 4. progress_seconds / total_duration 列 ↔ DTO 的 progress / duration，按契约字段名映射。
 *
 * @author xiangsipeng
 */
public interface AppPlayProgressMapper
{
    /**
     * 查询某用户对某剧集的进度
     *
     * @param userId    用户ID（当前登录身份）
     * @param episodeId 剧集ID
     * @return 进度；无记录为 null
     */
    public AppPlayProgressDto selectProgress(@Param("userId") Long userId, @Param("episodeId") Long episodeId);

    /**
     * 更新播放进度（仅已存在的进度行）
     *
     * @param userId    用户ID
     * @param episodeId 剧集ID
     * @param progress  播放进度（秒）
     * @param duration  总时长（秒，可空）
     * @return 影响行数（1=已更新，0=无进度行，需改走 insertProgress）
     */
    public int updateProgress(@Param("userId") Long userId, @Param("episodeId") Long episodeId,
            @Param("progress") Integer progress, @Param("duration") Integer duration);

    /**
     * 新增播放进度（首次播放该剧集时）
     *
     * @param userId    用户ID
     * @param episodeId 剧集ID
     * @param workId    剧集所属作品ID（NOT NULL）
     * @param progress  播放进度（秒）
     * @param duration  总时长（秒，可空）
     * @return 影响行数
     */
    public int insertProgress(@Param("userId") Long userId, @Param("episodeId") Long episodeId,
            @Param("workId") Long workId, @Param("progress") Integer progress, @Param("duration") Integer duration);
}