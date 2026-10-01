package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysEpisode;

/**
 * 剧集 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_episode 表。
 * 仅服务于：外部视频详情 / 统计详情接口返回该剧集列表（按 work_id 查询）。
 *
 * @author xiangsipeng
 */
public interface SysEpisodeMapper
{
    /**
     * 按作品ID查询剧集列表（按 episode_no 排序）
     *
     * @param query 查询条件（workId 必传）
     * @return 剧集集合
     */
    public List<SysEpisode> selectEpisodeListByWorkId(SysEpisode query);
}
