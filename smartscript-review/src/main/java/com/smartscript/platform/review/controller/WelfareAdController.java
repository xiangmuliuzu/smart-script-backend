package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.AdConfig;
import com.smartscript.platform.review.service.AdConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 广告福利任务配置Controller（福利与积分配置页面 - 广告福利任务 Tab）
 *
 * 只管理「看广告得积分」类任务（category=WELFARE）：
 * 配置任务名、每日可看次数（frequencyLimit）、单次奖励积分（rewardPoints）、
 * 生效时间、启停。广告位本身（开屏/插屏 category=PLACEMENT）由广告运营配置页管理，
 * 本接口强制 category=WELFARE，防止误操作广告位。
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/welfare/ad")
public class WelfareAdController {

    @Autowired
    private AdConfigService adConfigService;

    /**
     * 广告福利任务列表（强制 category=WELFARE）
     */
    @GetMapping("/list")
    public Map<String, Object> list(AdConfig query) {
        query.setCategory("WELFARE");
        List<AdConfig> list = adConfigService.selectAdConfigList(query);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 新增广告福利任务（强制 category=WELFARE）
     */
    @PostMapping
    public Map<String, Object> add(@RequestBody AdConfig adConfig) {
        adConfig.setCategory("WELFARE");
        if (adConfig.getSort() == null) adConfig.setSort(0);
        if (adConfig.getIsEnabled() == null) adConfig.setIsEnabled(1);
        if (adConfig.getFrequencyLimit() == null) adConfig.setFrequencyLimit(5);
        if (adConfig.getDailyCap() == null) adConfig.setDailyCap(20);
        if (adConfig.getWatchLimit() == null) adConfig.setWatchLimit(1);
        if (adConfig.getRewardPoints() == null) adConfig.setRewardPoints(0);
        adConfigService.insertAdConfig(adConfig);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 修改广告福利任务（强制 category=WELFARE，禁止改成广告位）
     */
    @PutMapping
    public Map<String, Object> edit(@RequestBody AdConfig adConfig) {
        adConfig.setCategory("WELFARE");
        adConfigService.updateAdConfig(adConfig);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 删除广告福利任务（删除前校验确为 WELFARE，防止误删广告位）
     */
    @DeleteMapping("/{adId}")
    public Map<String, Object> remove(@PathVariable("adId") Long adId) {
        Map<String, Object> result = new HashMap<>();
        AdConfig existing = adConfigService.selectAdConfigById(adId);
        if (existing == null) {
            result.put("code", 404);
            result.put("msg", "广告任务不存在");
            return result;
        }
        if (!"WELFARE".equals(existing.getCategory())) {
            result.put("code", 400);
            result.put("msg", "该记录为广告位，不允许在福利页面删除");
            return result;
        }
        adConfigService.deleteAdConfigById(adId);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }
}
