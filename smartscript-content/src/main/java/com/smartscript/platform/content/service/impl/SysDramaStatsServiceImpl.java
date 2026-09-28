package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysEpisode;
import com.smartscript.platform.content.domain.SysExternalDrama;
import com.smartscript.platform.content.domain.SysPlayHistory;
import com.smartscript.platform.content.mapper.SysEpisodeMapper;
import com.smartscript.platform.content.mapper.SysExternalDramaMapper;
import com.smartscript.platform.content.mapper.SysPlayHistoryMapper;
import com.smartscript.platform.content.service.ISysDramaStatsService;

/**
 * 外部视频统计 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama + sys_episode + sys_subscribe +
 * sys_work + sys_play_history 表。
 * 反推处理点：
 * 1. selectStatsList 委托 selectDramaStatsList 跨表聚合（按 related_work_id 聚合）。
 * 2. selectStatsById 详情接口需返回 stats + episodes 剧集列表，
 *    episodes 由本服务委托 SysEpisodeMapper 按 related_work_id 查询拼装。
 * 3. selectHistoryList 委托 SysPlayHistoryMapper 查询播放历史（含 episodeTitle）。
 *
 * @author xiangsipeng
 */
@Service
public class SysDramaStatsServiceImpl implements ISysDramaStatsService
{
    @Autowired
    private SysExternalDramaMapper dramaMapper;

    @Autowired
    private SysEpisodeMapper episodeMapper;

    @Autowired
    private SysPlayHistoryMapper playHistoryMapper;

    @Override
    public List<SysExternalDrama> selectStatsList(SysExternalDrama query)
    {
        return dramaMapper.selectDramaStatsList(query);
    }

    @Override
    public SysExternalDrama selectStatsById(Long workId)
    {
        SysExternalDrama stats = dramaMapper.selectDramaStatsById(workId);
        if (stats != null)
        {
            // 委托 SysEpisodeMapper 按 work_id 查询剧集列表拼装到 episodes 字段
            SysEpisode query = new SysEpisode();
            query.setWorkId(workId);
            List<SysEpisode> episodes = episodeMapper.selectEpisodeListByWorkId(query);
            stats.setEpisodes(episodes);
        }
        return stats;
    }

    @Override
    public List<SysPlayHistory> selectHistoryList(SysPlayHistory query)
    {
        return playHistoryMapper.selectPlayHistoryList(query);
    }
}
