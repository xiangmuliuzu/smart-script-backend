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
}
