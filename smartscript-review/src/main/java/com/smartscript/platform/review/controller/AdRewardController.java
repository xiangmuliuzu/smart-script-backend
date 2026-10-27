package com.smartscript.platform.review.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.utils.ip.IpUtils;
import com.smartscript.platform.review.service.AdConfigService;
import com.smartscript.platform.review.service.RiskCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 广告奖励Controller（供 App 端广告播放完成后回调，触发积分发放）
 *
 * @author smartscript
 */
@Anonymous
@RestController
@RequestMapping("/api/v1/ad")
public class AdRewardController {

    @Autowired
    private AdConfigService adConfigService;

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
     * 广告观看奖励发放
     * 请求体：{"adId": 2, "userId": 106}
     */
    @PostMapping("/reward")
    public Map<String, Object> reward(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long adId = params.get("adId") != null ? Long.valueOf(params.get("adId").toString()) : null;
            Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
            // 风控：广告奖励按 operation 频控防刷
            riskCheckService.checkFrequencyOrThrow(userId, IpUtils.getIpAddr(), "operation");
            return wrap(adConfigService.reward(adId, userId));
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "奖励发放失败: " + e.getMessage());
            return wrap(result);
        }
    }
}
