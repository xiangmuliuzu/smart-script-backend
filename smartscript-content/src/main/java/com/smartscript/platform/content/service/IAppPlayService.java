package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.dto.AppEpisodeDetailDto;
import com.smartscript.platform.content.dto.AppEpisodeItem;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppPlayHistoryItem;
import com.smartscript.platform.content.dto.AppPlayProgressDto;

/**
 * 剧集播放 服务（App 2.8.2 剧集列表 / 2.8.3 剧集详情 / 2.8.4 保存进度 /
 * 2.8.5 获取进度 / 2.8.6 播放历史）
 *
 * 依据：云端 script_platform_dev 库 sys_episode / sys_play_progress / sys_play_history 表 +
 * 接口文档表 2-95 ~ 2-99。
 *
 * 边界：
 *   - 2.8.2 / 2.8.3 为公开只读（游客可读）；
 *   - 2.8.4 / 2.8.5 / 2.8.6 为私有接口，归属一律取当前登录身份，写路径不接收 userId。
 *
 * @author xiangsipeng
 */
public interface IAppPlayService
{
    /**
     * 剧集列表（接口 2.8.2）
     *
     * @param workId 作品ID
     * @return 剧集列表（按集号升序；无剧集时为空列表，不报错）
     */
    public List<AppEpisodeItem> listEpisodes(Long workId);

    /**
     * 剧集详情（接口 2.8.3）
     *
     * @param episodeId 剧集ID
     * @return 剧集详情；不存在为 null，由控制层按 404 处理
     */
    public AppEpisodeDetailDto getEpisode(Long episodeId);

    /**
     * 保存播放进度（接口 2.8.4）
     *
     * 同时刷新播放历史（同事务）：进度落 sys_play_progress（按 user+episode 先更后插），
     * 历史落 sys_play_history（有行则刷新 play_time，无行则插入一条）。
     *
     * @param episodeId 剧集ID
     * @param progress  播放进度（秒，>=0）
     * @param duration  总时长（秒，可空）
     * @return true=已保存；false=剧集不存在，由控制层按 404 处理
     */
    public boolean saveProgress(Long episodeId, Integer progress, Integer duration);

    /**
     * 获取播放进度（接口 2.8.5）
     *
     * @param episodeId 剧集ID
     * @return 进度（无记录时 progress=0、duration=null）；剧集不存在为 null，由控制层按 404 处理
     */
    public AppPlayProgressDto getProgress(Long episodeId);

    /**
     * 播放历史（接口 2.8.6，分页）
     *
     * @param pageNum  页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果（total + list，仅含已上架未删除作品）
     */
    public AppPageResult<AppPlayHistoryItem> pageHistory(int pageNum, int pageSize);
}