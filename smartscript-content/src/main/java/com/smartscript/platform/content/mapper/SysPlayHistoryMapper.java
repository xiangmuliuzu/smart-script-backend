package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysPlayHistory;

/**
 * 播放历史 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_play_history 表。
 * 仅服务于：统计-播放历史列表接口（含 episodeTitle）。
 *
 * @author xiangsipeng
 */
public interface SysPlayHistoryMapper
{
    /**
     * 查询播放历史列表（含 episodeTitle，LEFT JOIN sys_episode）
     *
     * @param query 查询条件（workId/userId，按需传入）
     * @return 播放历史集合
     */
    public List<SysPlayHistory> selectPlayHistoryList(SysPlayHistory query);
}
