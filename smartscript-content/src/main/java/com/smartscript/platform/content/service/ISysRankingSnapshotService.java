package com.smartscript.platform.content.service;

import java.util.List;
import java.util.Map;
import com.smartscript.platform.content.domain.SysRankingSnapshot;

/**
 * 排行榜快照 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_ranking_snapshot 表 + PC 功能清单
 * （排行榜管理页：列表、详情、排名调整、重算）。
 * 重算支持 view_count/bookshelf_count/growth_score 三种 metric。
 *
 * @author xiangsipeng
 */
public interface ISysRankingSnapshotService
{
    /**
     * 查询榜单列表（含 workTitle）
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
     * 调整排名（只更新 rank_no 字段）
     *
     * @param snapshot 仅携带 rankingId + rankNo + updateBy
     * @return 影响行数
     */
    public int updateRankNo(SysRankingSnapshot snapshot);

    /**
     * 重算指定榜单类型的排名快照
     *
     * 算法：
     * 1. 取每作品浏览量 view_count、收藏量 bookshelf_count
     * 2. 若 metric=growth_score，再取上一期快照浏览量，按 work_id 计算增量
     * 3. 按 metric_value DESC 排序，rankNo 自增，score=metric_value
     * 4. 旧快照 status=0（失效），新快照批量插入 status=1
     *
     * @param rankingType 榜单类型
     * @param metric 排序指标（view_count/bookshelf_count/growth_score）
     * @param periodStart 周期开始（yyyy-MM-dd）
     * @param periodEnd 周期结束（yyyy-MM-dd）
     * @return 含 newSnapshotCount、oldInvalidatedCount
     */
    public Map<String, Object> recomputeRanking(String rankingType, String metric, String periodStart, String periodEnd);
}
