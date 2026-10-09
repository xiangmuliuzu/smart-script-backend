package com.smartscript.platform.content.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysRankingSnapshot;

/**
 * 排行榜快照 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_ranking_snapshot 表。
 * 含榜单列表查询、排名调整（单一职责）、重算所需聚合查询。
 *
 * @author xiangsipeng
 */
public interface SysRankingSnapshotMapper
{
    /**
     * 查询榜单列表（含 workTitle，JOIN sys_work）
     *
     * @param query 查询条件（rankingType/periodStart/periodEnd/status 精确）
     * @return 快照集合
     */
    public List<SysRankingSnapshot> selectRankingList(SysRankingSnapshot query);

    /**
     * 通过排行榜ID查询详情
     *
     * @param rankingId 排行榜ID
     * @return 快照对象
     */
    public SysRankingSnapshot selectRankingById(Long rankingId);

    /**
     * 调整排名（单一职责：仅改 rank_no）
     *
     * @param snapshot 仅携带 rankingId + rankNo + updateBy
     * @return 影响行数
     */
    public int updateRankNo(SysRankingSnapshot snapshot);

    // ---- 重算（recompute）所需聚合查询 ----

    /**
     * 查询所有未删作品的榜单指标（一次取全量，排序指标由榜单类型在服务层确定：
     * view→view_count、favorite→favorite_count、sale→sale_count、rating→rating）
     *
     * @return 每条含 work_id(Long)、view_count(Long)、favorite_count(Long)、sale_count(Long)、rating(BigDecimal)
     */
    public List<Map<String, Object>> selectWorkMetricStats();

    /**
     * 删除同榜单同周期的旧快照（重算=整体替换，避免撞唯一键 uk_ranking_work_period）
     *
     * @param rankingType 榜单类型
     * @param periodStart 周期开始
     * @param periodEnd 周期结束
     * @return 删除行数
     */
    public int deleteSnapshots(@Param("rankingType") String rankingType,
            @Param("periodStart") String periodStart,
            @Param("periodEnd") String periodEnd);

    /**
     * 批量插入新快照
     *
     * @param snapshots 快照列表
     * @return 影响行数
     */
    public int batchInsertSnapshots(List<SysRankingSnapshot> snapshots);
}
