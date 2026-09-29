package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysExternalDrama;

/**
 * 外部视频内容管理 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama + sys_episode 表。
 * 仅服务于：内容管理（增改查），不包含绑定/状态/统计（各自独立服务）。
 *
 * @author xiangsipeng
 */
public interface ISysExternalDramaService
{
    /**
     * 查询外部视频列表（含 relatedWorkTitle/channelName）
     *
     * @param query 查询条件（channelId/title 模糊/sourceType/authorizationStatus/status/syncStatus，按需传入）
     * @return 外部视频集合
     */
    public List<SysExternalDrama> selectDramaList(SysExternalDrama query);

    /**
     * 通过短剧ID查询详情（含 relatedWorkTitle/channelName + episodes 剧集列表）。
     * episodes 由本方法委托 SysEpisodeMapper 拼装到返回对象的 episodes 字段。
     *
     * @param dramaId 短剧ID
     * @return 外部视频对象（含 episodes）
     */
    public SysExternalDrama selectDramaById(Long dramaId);

    /**
     * 新增外部视频
     *
     * @param drama 外部视频
     * @return 影响行数
     */
    public int insertDrama(SysExternalDrama drama);

    /**
     * 修改外部视频（动态 set，仅更新非空字段）
     *
     * @param drama 外部视频
     * @return 影响行数
     */
    public int updateDrama(SysExternalDrama drama);
}
