package com.smartscript.platform.content.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysRankingSnapshot;
import com.smartscript.platform.content.mapper.SysRankingSnapshotMapper;
import com.smartscript.platform.content.service.ISysRankingSnapshotService;

/**
 * 排行榜快照 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_ranking_snapshot 表。
 * 反推处理点：
 * 1. rank_no 调整做防御性裁剪，只放行 rankNo；
 * 2. recompute 整体加 @Transactional，同榜单同周期先删旧快照再批量插入新快照，保证原子性
 *    （原「置失效再插入」会撞唯一键 uk_ranking_work_period，同周期二次重算必然报错，改为删除式替换）；
 * 3. 榜单类型白名单 view/favorite/sale/rating（与 App 端四榜对齐），指标由类型唯一确定，
 *    不再单独传 metric：view→view_count（阅读量）、favorite→favorite_count（收藏量，与 App 同源）、
 *    sale→sale_count（交易量）、rating→rating（评分）；
 * 4. status 语义统一为 0=有效 1=失效（若依通用约定，与 PC 页展示、种子数据一致）。
 *
 * @author xiangsipeng
 */
@Service
public class SysRankingSnapshotServiceImpl implements ISysRankingSnapshotService
{
    /** 状态：有效 */
    private static final String STATUS_ACTIVE = "0";

    /** 重算操作人 */
    private static final String RECOMPUTE_OPERATOR = "system-recompute";

    /** 日期格式（period_start/period_end） */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 榜单类型白名单：与 App 端四榜对齐 */
    private static final List<String> RANKING_TYPES = List.of("view", "favorite", "sale", "rating");

    @Autowired
    private SysRankingSnapshotMapper rankingMapper;

    @Override
    public List<SysRankingSnapshot> selectRankingList(SysRankingSnapshot query)
    {
        return rankingMapper.selectRankingList(query);
    }

    @Override
    public SysRankingSnapshot selectRankingById(Long rankingId)
    {
        return rankingMapper.selectRankingById(rankingId);
    }

    @Override
    public int updateRankNo(SysRankingSnapshot snapshot)
    {
        // 防御性裁剪：只放行 rankNo
        SysRankingSnapshot update = new SysRankingSnapshot();
        update.setRankingId(snapshot.getRankingId());
        update.setRankNo(snapshot.getRankNo());
        update.setUpdateBy(snapshot.getUpdateBy());
        return rankingMapper.updateRankNo(update);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Map<String, Object> recomputeRanking(String rankingType, String periodStart, String periodEnd)
    {
        // 参数校验：榜单类型白名单，指标由类型唯一确定
        if (StringUtils.isEmpty(rankingType) || StringUtils.isEmpty(periodStart) || StringUtils.isEmpty(periodEnd))
        {
            throw new ServiceException("rankingType/periodStart/periodEnd 不能为空");
        }
        if (!RANKING_TYPES.contains(rankingType))
        {
            throw new ServiceException("rankingType 仅支持 view/favorite/sale/rating");
        }

        // 1. 一次取全量未删作品的四项指标
        List<Map<String, Object>> workRows = rankingMapper.selectWorkMetricStats();

        // 2. 构建统一数据列表，按榜单类型确定排序指标
        List<Map<String, Object>> unified = new ArrayList<>();
        if (StringUtils.isNotEmpty(workRows))
        {
            for (Map<String, Object> row : workRows)
            {
                Long workId = ((Number) row.get("work_id")).longValue();
                Long viewCount = longValue(row.get("view_count"));
                Long favoriteCount = longValue(row.get("favorite_count"));
                Long saleCount = longValue(row.get("sale_count"));
                Object ratingVal = row.get("rating");
                BigDecimal rating = ratingVal == null ? BigDecimal.ZERO : new BigDecimal(ratingVal.toString());

                BigDecimal metricValue;
                if ("view".equals(rankingType))
                {
                    metricValue = BigDecimal.valueOf(viewCount);
                }
                else if ("favorite".equals(rankingType))
                {
                    metricValue = BigDecimal.valueOf(favoriteCount);
                }
                else if ("sale".equals(rankingType))
                {
                    metricValue = BigDecimal.valueOf(saleCount);
                }
                else
                {
                    metricValue = rating;
                }

                Map<String, Object> item = new HashMap<>();
                item.put("workId", workId);
                item.put("viewCount", viewCount);
                item.put("bookshelfCount", favoriteCount);
                item.put("metricValue", metricValue);
                unified.add(item);
            }
        }

        // 3. 按指标值 DESC 排序
        unified.sort((a, b) -> ((BigDecimal) b.get("metricValue")).compareTo((BigDecimal) a.get("metricValue")));

        // 4. 解析周期日期
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);
        Date periodStartDate;
        Date periodEndDate;
        try
        {
            periodStartDate = sdf.parse(periodStart);
            periodEndDate = sdf.parse(periodEnd);
        }
        catch (Exception e)
        {
            throw new ServiceException("periodStart/periodEnd 格式应为 yyyy-MM-dd");
        }
        Date snapshotTime = new Date();

        // 5. 构造快照列表（score=指标值；growth_score 不再作为排序指标，固定 0）
        List<SysRankingSnapshot> snapshots = new ArrayList<>();
        int rankNo = 1;
        for (Map<String, Object> item : unified)
        {
            SysRankingSnapshot s = new SysRankingSnapshot();
            s.setRankingType(rankingType);
            s.setPeriodStart(periodStartDate);
            s.setPeriodEnd(periodEndDate);
            s.setWorkId((Long) item.get("workId"));
            s.setRankNo(rankNo);
            s.setScore((BigDecimal) item.get("metricValue"));
            s.setViewCount((Long) item.get("viewCount"));
            s.setBookshelfCount((Long) item.get("bookshelfCount"));
            s.setGrowthScore(BigDecimal.ZERO);
            s.setSnapshotTime(snapshotTime);
            s.setStatus(STATUS_ACTIVE);
            s.setCreateBy(RECOMPUTE_OPERATOR);
            snapshots.add(s);
            rankNo++;
        }

        // 6. 同榜单同周期旧快照整体删除（避免撞唯一键 uk_ranking_work_period）
        int oldCount = rankingMapper.deleteSnapshots(rankingType, periodStart, periodEnd);
        // 7. 批量插入新快照
        int newCount = snapshots.isEmpty() ? 0 : rankingMapper.batchInsertSnapshots(snapshots);

        // 8. 返回汇总
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newSnapshotCount", newCount);
        result.put("oldRemovedCount", oldCount);
        return result;
    }

    /** Map 取数兜底：NULL 视为 0 */
    private static long longValue(Object v)
    {
        return v == null ? 0L : ((Number) v).longValue();
    }
}
