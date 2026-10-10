package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据总览（工作台）Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 数据总览聚合数据：指标卡 + 快捷入口 + 作品状态分布
     */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = dashboardService.selectOverview();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("data", data);
        return result;
    }

    /**
     * 交易趋势：week=近7日 / month=近30日 / year=近12月
     */
    @GetMapping("/trend")
    public Map<String, Object> trend(@RequestParam(value = "period", defaultValue = "week") String period) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> list = dashboardService.selectTrend(period);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 最近审核列表
     */
    @GetMapping("/recent-reviews")
    public Map<String, Object> recentReviews(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        Map<String, Object> result = new HashMap<>();
        if (limit < 1 || limit > 50) {
            limit = 10;
        }
        List<Map<String, Object>> list = dashboardService.selectRecentReviews(limit);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }
}
