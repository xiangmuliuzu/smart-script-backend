package com.smartscript.platform.review.service;

import com.smartscript.platform.review.mapper.StatsOverviewMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * 运营统计Service业务层处理
 *
 * @author smartscript
 */
@Service
public class StatsOverviewService {

    @Autowired
    private StatsOverviewMapper statsOverviewMapper;

    public Map<String, Object> selectOverview() {
        return statsOverviewMapper.selectOverview();
    }

    public List<Map<String, Object>> selectTrend(int days) {
        return statsOverviewMapper.selectTrend(days);
    }

    public List<Map<String, Object>> selectCreatorRank(int limit) {
        return statsOverviewMapper.selectCreatorRank(limit);
    }
}
