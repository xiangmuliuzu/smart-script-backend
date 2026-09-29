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
     * 查询所有未删作品的浏览量（metric=view_count 时使用）
     *
     * @return 每条含 work_id(Long) 与 view_count(Integer)
     */
    public List<Map<String, Object>> selectWorkViewCounts();

    /**
     * 查询每作品的收藏数（metric=bookshelf_count 时使用）
     *
     * @return 每条含 work_id(Long) 与 bookshelf_count(Long)
     */
    public List<Map<String, Object>> selectSubscribeCounts();

    /**
     * 查询同榜单类型上一期快照的浏览量（metric=growth_score 时使用）
     *
     * @param rankingType 榜单类型
     * @param periodStart 当前周期开始日期（取 period_end < 此值的最近一期）
     * @return 每条含 work_id(Long) 与 view_count(Long)
     */
    public List<Map<String, Object>> selectLatestPrevSnapshotViewCounts(@Param("rankingType") String rankingType,
            @Param("periodStart") String periodStart);

    /**
     * 使旧快照失效（单一职责：仅改 status=0）
     *
     * @param rankingType 榜单类型
     * @param periodStart 周期开始
     * @param periodEnd 周期结束
     * @return 影响行数
     */
    public int invalidateSnapshots(@Param("rankingType") String rankingType,
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
