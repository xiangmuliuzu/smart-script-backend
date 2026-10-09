package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.Report;
import com.smartscript.platform.review.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 举报/违规记录Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/review/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * 查询举报/违规记录列表
     */
    @GetMapping("/list")
    public Map<String, Object> list(Report report) {
        Map<String, Object> result = new HashMap<>();
        List<Report> list = reportService.selectReportList(report);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 查询举报/违规记录详情
     */
    @GetMapping("/detail/{reportId}")
    public Map<String, Object> detail(@PathVariable("reportId") Long reportId) {
        Map<String, Object> result = new HashMap<>();
        Report report = reportService.selectReportById(reportId);
        if (report == null) {
            result.put("code", 404);
            result.put("msg", "未找到该记录");
        } else {
            result.put("code", 200);
            result.put("msg", "操作成功");
            result.put("data", report);
        }
        return result;
    }

    /**
     * 新增举报/违规记录
     */
    @PostMapping("/create")
    public Map<String, Object> add(@RequestBody Report report) {
        Map<String, Object> result = new HashMap<>();
        reportService.insertReport(report);
        result.put("code", 200);
        result.put("msg", "添加成功");
        return result;
    }

    /**
     * 处理举报/违规记录
     */
    @PostMapping("/handle")
    public Map<String, Object> handle(@RequestBody Report report) {
        Map<String, Object> result = new HashMap<>();
        if (report.getReportId() == null) {
            result.put("code", 400);
            result.put("msg", "reportId不能为空");
            return result;
        }
        reportService.updateReport(report);
        result.put("code", 200);
        result.put("msg", "处理成功");
        return result;
    }

    /**
     * 删除举报/违规记录
     */
    @DeleteMapping("/{reportId}")
    public Map<String, Object> remove(@PathVariable("reportId") Long reportId) {
        Map<String, Object> result = new HashMap<>();
        reportService.deleteReportById(reportId);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }
}
