package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysExternalDrama;

/**
 * 外部视频 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama 表。
 * 同时服务于：外部视频内容管理（增改查）、绑定管理（绑/解绑）、状态管理（改状态/批量同步）、
 * 统计聚合（跨表 sys_episode/sys_subscribe/sys_work）。
 *
 * @author xiangsipeng
 */
public interface SysExternalDramaMapper
{
    /**
     * 查询外部视频列表（含 relatedWorkTitle/channelName，LEFT JOIN sys_work + sys_drama_channel）。
     * 同时服务于内容管理 / 绑定列表 / 状态列表，按入参过滤项区分。
     * 绑定场景入参 unboundOnly="1" 时仅查 related_work_id IS NULL 的未绑定记录。
     *
     * @param query 查询条件（channelId/title 模糊/sourceType/authorizationStatus/status/syncStatus/unboundOnly，按需传入）
     * @return 外部视频集合
     */
    public List<SysExternalDrama> selectDramaList(SysExternalDrama query);

    /**
     * 通过短剧ID查询详情（含 relatedWorkTitle/channelName）
     *
     * @param dramaId 短剧ID
     * @return 外部视频对象
     */
    public SysExternalDrama selectDramaById(Long dramaId);

    /**
     * 新增外部视频（所有 NOT NULL 字段；related_work_id 可空；created_at/updated_at 不写）
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

    /**
     * 修改关联作品ID（单一职责：仅改 related_work_id，可置 null）。
     * 用于绑定/解绑：解绑时 relatedWorkId 传 null，XML SET related_work_id = #{relatedWorkId}。
     *
     * @param drama 仅携带 dramaId + relatedWorkId（可空） + updateBy
     * @return 影响行数
     */
    public int updateRelatedWorkId(SysExternalDrama drama);

    /**
     * 修改外部视频状态（单一职责：仅改 status）
     *
     * @param drama 仅携带 dramaId + status + updateBy
     * @return 影响行数
     */
    public int updateDramaStatus(SysExternalDrama drama);

    /**
     * 批量修改同步状态（同步触发时将所选记录置为 syncing）
     *
     * @param dramaIds 短剧ID数组
     * @param syncStatus 目标同步状态（如 syncing）
     * @param updateBy 更新人
     * @return 影响行数
     */
    public int batchUpdateSyncStatus(@Param("dramaIds") Long[] dramaIds, @Param("syncStatus") String syncStatus, @Param("updateBy") String updateBy);

    /**
     * 统计聚合列表（按 related_work_id 聚合：总播放/订阅/剧集数 + 作品标题）。
     * 仅统计已绑定（related_work_id IS NOT NULL）的记录。
     *
     * @param query 查询条件（channelId/workId，按需传入）
     * @return 聚合统计集合
     */
    public List<SysExternalDrama> selectDramaStatsList(SysExternalDrama query);

    /**
     * 统计聚合单条（按 related_work_id 查询单个作品的聚合数据）
     *
     * @param workId 作品ID（即 related_work_id）
     * @return 聚合统计对象
     */
    public SysExternalDrama selectDramaStatsById(Long workId);
}
