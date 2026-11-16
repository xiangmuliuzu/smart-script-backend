package com.smartscript.platform.review.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.smartscript.platform.review.service.AiReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * AI审核Controller（作品提交后触发 AI 初筛打分）
 *
 * @author smartscript
 */
@Anonymous
@RestController
@RequestMapping("/api/v1/ai/review")
public class AiReviewController {

    @Autowired
    private AiReviewService aiReviewService;

    /**
     * AI初筛打分
     * 请求体：{"reviewId": 12, "content": "作品正文内容..."}
     */
    @PostMapping("/score")
    public Map<String, Object> score(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long reviewId = params.get("reviewId") != null ? Long.valueOf(params.get("reviewId").toString()) : null;
            String content = params.get("content") != null ? params.get("content").toString() : null;
            return aiReviewService.score(reviewId, content);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "AI初筛失败: " + e.getMessage());
            return result;
        }
    }
}
