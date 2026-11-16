package com.smartscript.platform.review.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.utils.ip.IpUtils;
import com.smartscript.platform.review.service.RiskCheckService;
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

    @Autowired
    private RiskCheckService riskCheckService;

    /**
     * 统一信封：{code, msg, data:{...}}，App 端按标准若依信封解析。
     */
    private Map<String, Object> wrap(Map<String, Object> result) {
        Map<String, Object> data = new HashMap<>(result);
        data.remove("code");
        data.remove("msg");
        Map<String, Object> env = new HashMap<>();
        env.put("code", result.getOrDefault("code", 200));
        env.put("msg", result.getOrDefault("msg", "success"));
        env.put("data", data);
        return env;
    }

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
            // 风控：领取奖励按 operation 频控防刷
            riskCheckService.checkFrequencyOrThrow(userId, IpUtils.getIpAddr(), "operation");
            return wrap(taskConfigService.claimTask(taskCode, userId));
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "领取失败: " + e.getMessage());
            return wrap(result);
        }
    }

    /**
     * 用户上报任务行为进度（App 完成真实行为后调用，如阅读/上传/登录）
     * 请求体：{"taskCode": "READ_CHAPTER", "userId": 106, "increment": 1}
     */
    @PostMapping("/progress")
    public Map<String, Object> progress(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String taskCode = params.get("taskCode") != null ? params.get("taskCode").toString() : null;
            Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
            // 风控：行为进度上报按 operation 频控防刷
            riskCheckService.checkFrequencyOrThrow(userId, IpUtils.getIpAddr(), "operation");
            int increment = params.get("increment") != null ? Integer.parseInt(params.get("increment").toString()) : 1;
            return wrap(taskConfigService.reportProgress(taskCode, userId, increment));
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "上报进度失败: " + e.getMessage());
            return wrap(result);
        }
    }
}
