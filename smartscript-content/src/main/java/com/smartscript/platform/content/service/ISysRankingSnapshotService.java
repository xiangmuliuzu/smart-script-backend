package com.smartscript.platform.content.service;

import java.util.List;
import java.util.Map;
import com.smartscript.platform.content.domain.SysRankingSnapshot;

/**
 * 排行榜快照 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_ranking_snapshot 表 + PC 功能清单
 * （排行榜管理页：列表、详情、排名调整、重算）。
 * 重算支持 view/favorite/sale/rating 四种榜单类型（与 App 端四榜对齐），指标由类型唯一确定。
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
     * 1. 一次取全量未删作品的指标（view_count/favorite_count/sale_count/rating），
     *    按榜单类型确定排序指标：view→view_count、favorite→favorite_count、sale→sale_count、rating→rating
     * 2. 按指标值 DESC 排序，rankNo 自增，score=指标值
     * 3. 同榜单同周期的旧快照整体删除（重算=替换），新快照批量插入（status=0 有效）
     *
     * @param rankingType 榜单类型（view/favorite/sale/rating）
     * @param periodStart 周期开始（yyyy-MM-dd）
     * @param periodEnd 周期结束（yyyy-MM-dd）
     * @return 含 newSnapshotCount、oldRemovedCount
     */
    public Map<String, Object> recomputeRanking(String rankingType, String periodStart, String periodEnd);
}
