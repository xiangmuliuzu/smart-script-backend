package com.smartscript.platform.review.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.smartscript.platform.review.service.AdConfigService;
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
            return adConfigService.reward(adId, userId);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "奖励发放失败: " + e.getMessage());
            return result;
        }
    }
}
