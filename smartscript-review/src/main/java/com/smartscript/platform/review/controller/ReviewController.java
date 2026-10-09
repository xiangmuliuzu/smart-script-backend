package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审核记录Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/review")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    /**
     * 查询审核记录列表
     */
    @GetMapping("/list")
    public Map<String, Object> list(ReviewRecord reviewRecord) {
        Map<String, Object> result = new HashMap<>();
        List<ReviewRecord> list = reviewService.selectReviewRecordList(reviewRecord);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 查询审核记录详情
     */
    @GetMapping("/detail/{reviewId}")
    public Map<String, Object> detail(@PathVariable("reviewId") Long reviewId) {
        Map<String, Object> result = new HashMap<>();
        ReviewRecord reviewRecord = reviewService.selectReviewRecordById(reviewId);
        if (reviewRecord == null) {
            result.put("code", 404);
            result.put("msg", "未找到该审核记录");
        } else {
            result.put("code", 200);
            result.put("msg", "操作成功");
            result.put("data", reviewRecord);
        }
        return result;
    }

    /**
     * 新增审核记录
     */
    @PostMapping
    public Map<String, Object> add(@RequestBody ReviewRecord reviewRecord) {
        Map<String, Object> result = new HashMap<>();
        reviewService.insertReviewRecord(reviewRecord);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 修改审核记录
     */
    @PutMapping
    public Map<String, Object> edit(@RequestBody ReviewRecord reviewRecord) {
        Map<String, Object> result = new HashMap<>();
        reviewService.updateReviewRecord(reviewRecord);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 删除审核记录
     */
    @DeleteMapping("/{reviewId}")
    public Map<String, Object> remove(@PathVariable("reviewId") Long reviewId) {
        Map<String, Object> result = new HashMap<>();
        reviewService.deleteReviewRecordById(reviewId);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 审核操作（通过/驳回/发回修改）
     */
    @PostMapping("/operate")
    public Map<String, Object> operate(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        Long reviewId = Long.valueOf(params.get("reviewId").toString());
        String status = params.get("status").toString();
        String reviewOpinion = params.get("reviewOpinion") != null ? params.get("reviewOpinion").toString() : "";
        Long reviewerId = params.get("reviewerId") != null ? Long.valueOf(params.get("reviewerId").toString()) : 1L;
        reviewService.operateReview(reviewId, status, reviewOpinion, reviewerId);
        result.put("code", 200);
        result.put("msg", "审核操作成功");
        return result;
    }

    /**
     * 批量分配审核任务
     */
    @PostMapping("/batch-assign")
    public Map<String, Object> batchAssign(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        List<Long> reviewIds = (List<Long>) params.get("reviewIds");
        Long reviewerId = Long.valueOf(params.get("reviewerId").toString());
        reviewService.batchAssign(reviewIds, reviewerId);
        result.put("code", 200);
        result.put("msg", "批量分配成功");
        return result;
    }

    /**
     * 审核日志查询
     */
    @GetMapping("/logs")
    public Map<String, Object> logs(Long reviewId) {
        Map<String, Object> result = new HashMap<>();
        java.util.List<com.smartscript.platform.review.domain.ReviewLog> logs = reviewService.selectReviewLogs(reviewId);
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (com.smartscript.platform.review.domain.ReviewLog l : logs) {
            Map<String, Object> m = new HashMap<>();
            m.put("logId", l.getLogId());
            m.put("reviewId", l.getReviewId());
            m.put("operator", l.getOperatorName());
            m.put("action", l.getAction());
            m.put("beforeStatus", l.getBeforeStatus());
            m.put("afterStatus", l.getAfterStatus());
            m.put("reviewOpinion", l.getReviewOpinion());
            m.put("createTime", l.getCreateTime() == null ? "" : l.getCreateTime());
            rows.add(m);
        }
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", rows);
        result.put("total", rows.size());
        return result;
    }

    /**
     * 审核统计（工作台总览卡片数据）
     */
    @GetMapping("/statistics")
    public Map<String, Object> statistics() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> stats = reviewService.selectReviewStatistics();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("pending", stats.get("pending"));
        result.put("aiReviewing", stats.get("aiReviewing"));
        result.put("approved", stats.get("approved"));
        result.put("rejected", stats.get("rejected"));
        result.put("revision", stats.get("revision"));
        return result;
    }

    /**
     * 导出审核报告（CSV下载）
     */
    @GetMapping("/export")
    public void export(jakarta.servlet.http.HttpServletResponse response) {
        try {
            List<ReviewRecord> list = reviewService.selectReviewRecordList(new ReviewRecord());
            StringBuilder sb = new StringBuilder();
            sb.append("编号,作品名称,类型,题材,作者,提交时间,AI评分,状态\n");
            for (ReviewRecord r : list) {
                sb.append(r.getReviewId()).append(",")
                  .append(nullSafe(r.getWorkTitle())).append(",")
                  .append(nullSafe(r.getWorkType())).append(",")
                  .append(nullSafe(r.getGenreName())).append(",")
                  .append(nullSafe(r.getAuthorName())).append(",")
                  .append(r.getCreateTime() == null ? "" : r.getCreateTime()).append(",")
                  .append(r.getAiScore() == null ? "" : r.getAiScore()).append(",")
                  .append(nullSafe(r.getStatus())).append("\n");
            }
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=review_report.csv");
            response.getWriter().write("\uFEFF" + sb.toString());
        } catch (Exception e) {
            throw new RuntimeException("导出审核报告失败", e);
        }
    }

    /**
     * 导出审核日志（CSV下载）
     */
    @GetMapping("/logs/export")
    public void exportLogs(jakarta.servlet.http.HttpServletResponse response) {
        try {
            java.util.List<com.smartscript.platform.review.domain.ReviewLog> logs = reviewService.selectReviewLogs(null);
            StringBuilder sb = new StringBuilder();
            sb.append("时间,操作人,审核记录ID,操作,变更前状态,变更后状态\n");
            for (com.smartscript.platform.review.domain.ReviewLog log : logs) {
                sb.append(nullSafe(log.getCreateTime() == null ? "" : String.valueOf(log.getCreateTime()))).append(",")
                  .append(nullSafe(log.getOperatorName())).append(",")
                  .append(nullSafe(String.valueOf(log.getReviewId()))).append(",")
                  .append(nullSafe(log.getAction())).append(",")
                  .append(nullSafe(log.getBeforeStatus())).append(",")
                  .append(nullSafe(log.getAfterStatus())).append("\n");
            }
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=review_logs.csv");
            response.getWriter().write("\uFEFF" + sb.toString());
        } catch (Exception e) {
            throw new RuntimeException("导出审核日志失败", e);
        }
    }

    /**
     * null安全转换
     */
    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
