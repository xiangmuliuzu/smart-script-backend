package com.smartscript.platform.content.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
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
 * 2. recompute 整体加 @Transactional，先失效旧快照再批量插入新快照，保证原子性；
 * 3. metric 仅支持 view_count/bookshelf_count/growth_score 三种，其他抛 ServiceException；
 * 4. growth_score 需查上一期同榜单类型快照的 view_count 作差。
 *
 * @author xiangsipeng
 */
@Service
public class SysRankingSnapshotServiceImpl implements ISysRankingSnapshotService
{
    /** 状态：有效 */
    private static final String STATUS_ACTIVE = "1";

    /** 重算操作人 */
    private static final String RECOMPUTE_OPERATOR = "system-recompute";

    /** 日期格式（period_start/period_end） */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

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
    public Map<String, Object> recomputeRanking(String rankingType, String metric, String periodStart, String periodEnd)
    {
        // 参数校验
        if (StringUtils.isEmpty(rankingType) || StringUtils.isEmpty(metric)
                || StringUtils.isEmpty(periodStart) || StringUtils.isEmpty(periodEnd))
        {
            throw new ServiceException("rankingType/metric/periodStart/periodEnd 不能为空");
        }
        if (!"view_count".equals(metric) && !"bookshelf_count".equals(metric) && !"growth_score".equals(metric))
        {
            throw new ServiceException("metric 仅支持 view_count/bookshelf_count/growth_score");
        }

        // 1. 取每作品浏览量
        List<Map<String, Object>> viewRows = rankingMapper.selectWorkViewCounts();
        // 2. 取每作品收藏量
        List<Map<String, Object>> subRows = rankingMapper.selectSubscribeCounts();
        // 3. metric=growth_score 时取上一期快照浏览量
        Map<Long, Long> prevViewMap = new HashMap<>();
        if ("growth_score".equals(metric))
        {
            List<Map<String, Object>> prevRows = rankingMapper.selectLatestPrevSnapshotViewCounts(rankingType, periodStart);
            if (StringUtils.isNotEmpty(prevRows))
            {
                for (Map<String, Object> row : prevRows)
                {
                    Long workId = ((Number) row.get("work_id")).longValue();
                    Long vc = ((Number) row.get("view_count")).longValue();
                    prevViewMap.put(workId, vc);
                }
            }
        }

        // 4. 收藏量按 work_id 索引，便于按作品合并
        Map<Long, Long> bookshelfMap = new HashMap<>();
        if (StringUtils.isNotEmpty(subRows))
        {
            for (Map<String, Object> row : subRows)
            {
                Long workId = ((Number) row.get("work_id")).longValue();
                Long bc = ((Number) row.get("bookshelf_count")).longValue();
                bookshelfMap.put(workId, bc);
            }
        }

        // 5. 构建统一数据列表
        List<Map<String, Object>> unified = new ArrayList<>();
        if (StringUtils.isNotEmpty(viewRows))
        {
            for (Map<String, Object> row : viewRows)
            {
                Long workId = ((Number) row.get("work_id")).longValue();
                Long viewCount = ((Number) row.get("view_count")).longValue();
                Long bookshelfCount = bookshelfMap.getOrDefault(workId, 0L);
                BigDecimal growthScore;
                if ("growth_score".equals(metric))
                {
                    Long prev = prevViewMap.getOrDefault(workId, 0L);
                    growthScore = BigDecimal.valueOf(viewCount - prev);
                }
                else
                {
                    growthScore = BigDecimal.ZERO;
                }

                BigDecimal metricValue;
                if ("view_count".equals(metric))
                {
                    metricValue = BigDecimal.valueOf(viewCount);
                }
                else if ("bookshelf_count".equals(metric))
                {
                    metricValue = BigDecimal.valueOf(bookshelfCount);
                }
                else
                {
                    metricValue = growthScore;
                }

                Map<String, Object> item = new HashMap<>();
                item.put("workId", workId);
                item.put("viewCount", viewCount);
                item.put("bookshelfCount", bookshelfCount);
                item.put("growthScore", growthScore);
                item.put("metricValue", metricValue);
                unified.add(item);
            }
        }

        // 6. 按 metric_value DESC 排序
        unified.sort(new Comparator<Map<String, Object>>()
        {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b)
            {
                BigDecimal va = (BigDecimal) a.get("metricValue");
                BigDecimal vb = (BigDecimal) b.get("metricValue");
                return vb.compareTo(va);
            }
        });

        // 7. 解析周期日期并构造快照列表
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
            s.setGrowthScore((BigDecimal) item.get("growthScore"));
            s.setSnapshotTime(snapshotTime);
            s.setStatus(STATUS_ACTIVE);
            s.setCreateBy(RECOMPUTE_OPERATOR);
            snapshots.add(s);
            rankNo++;
        }

        // 8. 失效旧快照
        int oldCount = rankingMapper.invalidateSnapshots(rankingType, periodStart, periodEnd);
        // 9. 批量插入新快照
        int newCount = snapshots.isEmpty() ? 0 : rankingMapper.batchInsertSnapshots(snapshots);

        // 10. 返回汇总
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newSnapshotCount", newCount);
        result.put("oldInvalidatedCount", oldCount);
        return result;
    }
}
