package com.smartscript.platform.review.service;

import com.smartscript.platform.review.mapper.DashboardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据总览Service业务层处理
 *
 * @author smartscript
 */
@Service
public class DashboardService {

    @Autowired
    private DashboardMapper dashboardMapper;

    /** 作品状态中文与颜色映射 */
    private static final Map<String, String[]> STATUS_MAP = new LinkedHashMap<>();
    static {
        STATUS_MAP.put("pending", new String[]{"待审核", "#E6A23C"});
        STATUS_MAP.put("approved", new String[]{"已通过", "#67C23A"});
        STATUS_MAP.put("on_shelf", new String[]{"已上架", "#409EFF"});
        STATUS_MAP.put("off_shelf", new String[]{"已下架", "#909399"});
        STATUS_MAP.put("draft", new String[]{"草稿", "#F56C6C"});
    }

    public Map<String, Object> selectOverview() {
        Map<String, Object> row = dashboardMapper.selectOverviewRow();
        List<Map<String, Object>> dist = dashboardMapper.selectWorkStatusDist();

        Map<String, Object> data = new HashMap<>();

        // 指标卡
        List<Map<String, Object>> stats = new ArrayList<>();
        long pendingWorks = num(row, "pendingWorks");
        long listedWorks = num(row, "listedWorks");
        double monthTrade = num(row, "monthTrade");
        long activeUsers = num(row, "activeUsers");
        stats.add(stat("待审核作品", pendingWorks, trend(num(row, "newPending7"), num(row, "newPendingPrev"))));
        stats.add(stat("已授权作品", listedWorks, trend(num(row, "newListed7"), num(row, "newListedPrev"))));
        stats.add(stat("本月交易额", "¥" + fmtWan(monthTrade) + "万", trend(monthTrade, num(row, "prevMonthTrade"))));
        stats.add(stat("活跃用户数", activeUsers, trend(activeUsers, num(row, "prevActiveUsers"))));

        // 快捷入口
        List<Map<String, Object>> quick = new ArrayList<>();
        quick.add(quickItem("作品审核", num(row, "pendingWorks"), "待审核", "/copyright/ai-review/review"));
        quick.add(quickItem("订单管理", num(row, "pendingOrders"), "待处理", "/trade/orders"));
        quick.add(quickItem("用户管理", num(row, "pendingUsers"), "待审核", "/user"));
        quick.add(quickItem("风控管理", num(row, "pendingRisk"), "待处理", "/risk"));

        // 作品状态分布
        List<Map<String, Object>> statusList = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> d : dist) {
            total += ((Number) d.get("cnt")).longValue();
        }
        for (Map<String, Object> d : dist) {
            String status = String.valueOf(d.get("status"));
            long cnt = ((Number) d.get("cnt")).longValue();
            String[] cfg = STATUS_MAP.get(status);
            if (cfg == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("name", cfg[0]);
            item.put("value", cnt);
            item.put("percent", total == 0 ? 0 : Math.round(cnt * 1000.0 / total) / 10.0);
            item.put("color", cfg[1]);
            statusList.add(item);
        }

        data.put("stats", stats);
        data.put("quick", quick);
        data.put("statusDist", statusList);
        return data;
    }

    public List<Map<String, Object>> selectTrend(String period) {
        List<Map<String, Object>> list;
        if ("year".equals(period)) {
            list = dashboardMapper.selectTrendYear();
        } else if ("month".equals(period)) {
            list = dashboardMapper.selectTrendMonth();
        } else {
            list = dashboardMapper.selectTrendWeek();
        }
        return list;
    }

    public List<Map<String, Object>> selectRecentReviews(int limit) {
        return dashboardMapper.selectRecentReviews(limit);
    }

    private Map<String, Object> stat(String title, Object value, double trendValue) {
        Map<String, Object> item = new HashMap<>();
        item.put("title", title);
        item.put("value", value);
        item.put("trend", trendValue);
        return item;
    }

    private Map<String, Object> quickItem(String title, long count, String label, String route) {
        Map<String, Object> item = new HashMap<>();
        item.put("title", title);
        item.put("count", count);
        item.put("label", label);
        item.put("route", route);
        return item;
    }

    /** 环比百分比：本期 vs 上期 */
    private double trend(double cur, double prev) {
        if (prev <= 0) {
            return cur > 0 ? 100.0 : 0.0;
        }
        return Math.round((cur - prev) / prev * 1000.0) / 10.0;
    }

    private long num(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }

    private double dnum(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            return 0d;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return Double.parseDouble(String.valueOf(v));
    }

    private String fmtWan(double amountYuan) {
        double wan = amountYuan / 10000.0;
        if (wan == Math.floor(wan) && wan < 100) {
            return String.valueOf((long) wan);
        }
        return new BigDecimal(wan).setScale(1, BigDecimal.ROUND_HALF_UP).toPlainString();
    }
}
