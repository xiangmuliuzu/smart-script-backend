package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppEpisodeDetailDto;
import com.smartscript.platform.content.dto.AppEpisodeItem;

/**
 * 剧集浏览 数据层（App 2.8.2 剧集列表 / 2.8.3 剧集详情）
 *
 * 依据：云端 script_platform_dev 库 sys_episode 表 + 接口文档表 2-95 / 2-96。
 *
 * 反推处理点：
 * 1. 按 work_id 查列表、按 episode_id 查详情，均为只读、仅 App 浏览投影，不写库。
 * 2. 不按 status 过滤：该列在库端无权威枚举且无写入方，既有 PC 侧
 *    SysEpisodeMapper.selectEpisodeListByWorkId 同样不按 status 过滤，沿用同一口径。
 * 3. is_free 为 tinyint，映射到 DTO 的 String（"0"/"1"）。
 *
 * @author xiangsipeng
 */
public interface AppEpisodeMapper
{
    /**
     * 按作品ID查询剧集列表（按 episode_no 升序）
     *
     * @param workId 作品ID
     * @return 剧集列表项
     */
    public List<AppEpisodeItem> selectAppEpisodesByWorkId(@Param("workId") Long workId);

    /**
     * 按剧集ID查询详情
     *
     * @param episodeId 剧集ID
     * @return 剧集详情；不存在为 null
     */
    public AppEpisodeDetailDto selectAppEpisodeById(@Param("episodeId") Long episodeId);
}