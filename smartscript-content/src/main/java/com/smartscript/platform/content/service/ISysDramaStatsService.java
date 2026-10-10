package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysExternalDrama;
import com.smartscript.platform.content.domain.SysPlayHistory;

/**
 * 外部视频统计 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama + sys_episode + sys_subscribe +
 * sys_work + sys_play_history 表。
 * 反推处理点：跨表聚合（按 related_work_id 聚合播放/订阅/剧集数）；播放历史独立列表。
 *
 * @author xiangsipeng
 */
public interface ISysDramaStatsService
{
    /**
     * 查询统计聚合列表（按 related_work_id 聚合：总播放/订阅/剧集数 + 作品标题）
     *
     * @param query 查询条件（channelId/workId，按需传入）
     * @return 聚合统计集合
     */
    public List<SysExternalDrama> selectStatsList(SysExternalDrama query);

    /**
     * 通过作品ID查询统计聚合详情（含 episodes 剧集列表）。
     * episodes 由本方法委托 SysEpisodeMapper 拼装到返回对象的 episodes 字段。
     *
     * @param workId 作品ID（即 related_work_id）
     * @return 聚合统计对象（含 episodes）
     */
    public SysExternalDrama selectStatsById(Long workId);

    /**
     * 查询播放历史列表（含 episodeTitle，LEFT JOIN sys_episode）
     *
     * @param query 查询条件（workId/userId，按需传入）
     * @return 播放历史集合
     */
    public List<SysPlayHistory> selectHistoryList(SysPlayHistory query);
}
