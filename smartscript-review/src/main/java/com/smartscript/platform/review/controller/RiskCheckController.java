package com.smartscript.platform.review.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.smartscript.platform.review.service.RiskCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 风控校验Controller（App 端提交作品/评论/内容前调用）
 *
 * @author smartscript
 */
@Anonymous
@RestController
@RequestMapping("/api/v1/risk")
public class RiskCheckController {

    @Autowired
    private RiskCheckService riskCheckService;

    /**
     * 内容风控检查
     * 请求体：{"userId": 106, "content": "作品正文..."}
     */
    @PostMapping("/check")
    public Map<String, Object> check(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
            String content = params.get("content") != null ? params.get("content").toString() : null;
            return riskCheckService.check(userId, content);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "风控检查失败: " + e.getMessage());
            return result;
        }
    }
}
