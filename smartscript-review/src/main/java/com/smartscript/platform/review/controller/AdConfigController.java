package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.AdConfig;
import com.smartscript.platform.review.service.AdConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 广告运营配置Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/operation/ad-config")
public class AdConfigController {

    @Autowired
    private AdConfigService adConfigService;

    /**
     * 查询广告配置列表
     */
    @GetMapping("/list")
    public Map<String, Object> list(AdConfig adConfig) {
        Map<String, Object> result = new HashMap<>();
        List<AdConfig> list = adConfigService.selectAdConfigList(adConfig);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 查询广告配置详情
     */
    @GetMapping("/detail/{adId}")
    public Map<String, Object> detail(@PathVariable("adId") Long adId) {
        Map<String, Object> result = new HashMap<>();
        AdConfig adConfig = adConfigService.selectAdConfigById(adId);
        if (adConfig == null) {
            result.put("code", 404);
            result.put("msg", "未找到该广告配置");
        } else {
            result.put("code", 200);
            result.put("msg", "操作成功");
            result.put("data", adConfig);
        }
        return result;
    }

    /**
     * 新增广告配置
     */
    @PostMapping
    public Map<String, Object> add(@RequestBody AdConfig adConfig) {
        Map<String, Object> result = new HashMap<>();
        if (adConfig.getSort() == null) {
            adConfig.setSort(0);
        }
        if (adConfig.getIsEnabled() == null) {
            adConfig.setIsEnabled(1);
        }
        if (adConfig.getFrequencyLimit() == null) {
            adConfig.setFrequencyLimit(1);
        }
        if (adConfig.getDailyCap() == null) {
            adConfig.setDailyCap(5);
        }
        if (adConfig.getWatchLimit() == null) {
            adConfig.setWatchLimit(5);
        }
        if (adConfig.getRewardPoints() == null) {
            adConfig.setRewardPoints(0);
        }
        adConfigService.insertAdConfig(adConfig);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 修改广告配置
     */
    @PutMapping
    public Map<String, Object> edit(@RequestBody AdConfig adConfig) {
        Map<String, Object> result = new HashMap<>();
        adConfigService.updateAdConfig(adConfig);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 删除广告配置
     */
    @DeleteMapping("/{adId}")
    public Map<String, Object> remove(@PathVariable("adId") Long adId) {
        Map<String, Object> result = new HashMap<>();
        adConfigService.deleteAdConfigById(adId);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }
}
