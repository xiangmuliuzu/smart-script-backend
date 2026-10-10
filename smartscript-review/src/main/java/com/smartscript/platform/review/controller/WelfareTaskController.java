package com.smartscript.platform.review.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.smartscript.platform.review.service.TaskConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 福利任务领取Controller（供 App 端在业务完成后调用，触发任务奖励发放）
 *
 * @author smartscript
 */
@Anonymous
@RestController
@RequestMapping("/api/v1/welfare/task")
public class WelfareTaskController {

    @Autowired
    private TaskConfigService taskConfigService;

    /**
     * 用户领取任务奖励
     * 请求体：{"taskCode": "DAILY_LOGIN", "userId": 106}
     */
    @PostMapping("/claim")
    public Map<String, Object> claim(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String taskCode = params.get("taskCode") != null ? params.get("taskCode").toString() : null;
            Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
            return taskConfigService.claimTask(taskCode, userId);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "领取失败: " + e.getMessage());
            return result;
        }
    }
}
