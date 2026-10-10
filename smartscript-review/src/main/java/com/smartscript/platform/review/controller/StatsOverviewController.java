package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.service.StatsOverviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营数据总览Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/statistics")
public class StatsOverviewController {

    @Autowired
    private StatsOverviewService statsOverviewService;

    /**
     * 运营概览卡片
     */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = statsOverviewService.selectOverview();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("data", data);
        return result;
    }

    /**
     * 近N天用户/作品趋势
     */
    @GetMapping("/trend")
    public Map<String, Object> trend(@RequestParam(value = "days", defaultValue = "7") int days) {
        Map<String, Object> result = new HashMap<>();
        if (days < 1 || days > 30) {
            days = 7;
        }
        List<Map<String, Object>> list = statsOverviewService.selectTrend(days);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 创作者作品排行
     */
    @GetMapping("/creator-rank")
    public Map<String, Object> creatorRank(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        Map<String, Object> result = new HashMap<>();
        if (limit < 1 || limit > 50) {
            limit = 10;
        }
        List<Map<String, Object>> list = statsOverviewService.selectCreatorRank(limit);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }
}
