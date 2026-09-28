package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysExternalDrama;

/**
 * 外部视频状态管理 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama 表（status/sync_status 字段）。
 * 反推处理点：状态变更/批量同步独立成接口，与内容管理/绑定管理解耦。
 *
 * @author xiangsipeng
 */
public interface ISysDramaStatusService
{
    /**
     * 查询状态列表（含 relatedWorkTitle/channelName，常按 syncStatus 过滤）
     *
     * @param query 查询条件（channelId/title 模糊/status/syncStatus，按需传入）
     * @return 外部视频集合
     */
    public List<SysExternalDrama> selectStatusList(SysExternalDrama query);

    /**
     * 修改外部视频状态（单一职责：仅改 status）
     *
     * @param drama 仅携带 dramaId + status + updateBy
     * @return 影响行数
     */
    public int changeStatus(SysExternalDrama drama);

    /**
     * 批量同步（将所选记录的 sync_status 置为 syncing）
     *
     * @param dramaIds 短剧ID数组
     * @param updateBy 更新人
     * @return 影响行数
     */
    public int syncDramas(Long[] dramaIds, String updateBy);
}
