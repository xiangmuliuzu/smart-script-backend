package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.AdConfig;
import com.smartscript.platform.review.mapper.AdConfigMapper;
import com.smartscript.platform.review.mapper.PointsRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 广告配置Service业务层处理
 *
 * @author smartscript
 */
@Service
public class AdConfigService {

    @Autowired
    private AdConfigMapper adConfigMapper;

    @Autowired
    private PointsRecordMapper pointsRecordMapper;

    @Autowired
    private PointsService pointsService;

    public List<AdConfig> selectAdConfigList(AdConfig adConfig) {
        return adConfigMapper.selectAdConfigList(adConfig);
    }

    public AdConfig selectAdConfigById(Long adId) {
        return adConfigMapper.selectAdConfigById(adId);
    }

    public int insertAdConfig(AdConfig adConfig) {
        return adConfigMapper.insertAdConfig(adConfig);
    }

    public int updateAdConfig(AdConfig adConfig) {
        return adConfigMapper.updateAdConfig(adConfig);
    }

    public int deleteAdConfigById(Long adId) {
        return adConfigMapper.deleteAdConfigById(adId);
    }

    /**
     * 广告观看奖励发放（App 端广告播放完成后回调）
     *
     * @param adId  广告ID
     * @param userId 用户ID
     * @return {code, msg, balance(发放后余额), rewardPoints}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reward(Long adId, Long userId) {
        Map<String, Object> result = new HashMap<>();
        if (adId == null || userId == null) {
            result.put("code", 400);
            result.put("msg", "adId与userId必填");
            return result;
        }
        AdConfig ad = adConfigMapper.selectAdConfigById(adId);
        if (ad == null) {
            result.put("code", 404);
            result.put("msg", "广告不存在: " + adId);
            return result;
        }
        if (ad.getIsEnabled() == null || ad.getIsEnabled() != 1) {
            result.put("code", 400);
            result.put("msg", "广告未启用");
            return result;
        }
        Date now = new Date();
        if (ad.getStartDate() != null && now.before(ad.getStartDate())) {
            result.put("code", 400);
            result.put("msg", "广告未开始");
            return result;
        }
        if (ad.getEndDate() != null && now.after(ad.getEndDate())) {
            result.put("code", 400);
            result.put("msg", "广告已结束");
            return result;
        }
        int reward = ad.getRewardPoints() == null ? 0 : ad.getRewardPoints();
        if (reward <= 0) {
            result.put("code", 400);
            result.put("msg", "该广告未配置奖励积分");
            return result;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd").format(now);
        // 每用户每广告每日频次限制（frequency_limit）
        if (ad.getFrequencyLimit() != null && ad.getFrequencyLimit() > 0) {
            int claimed = pointsRecordMapper.countAdClaims(userId, adId, today);
            if (claimed >= ad.getFrequencyLimit()) {
                result.put("code", 400);
                result.put("msg", "今日该广告奖励已达上限(" + ad.getFrequencyLimit() + "次)");
                return result;
            }
        }
        // 每用户每日全部广告总次数限制（daily_cap）
        if (ad.getDailyCap() != null && ad.getDailyCap() > 0) {
            int daily = pointsRecordMapper.countAdDaily(userId, today);
            if (daily >= ad.getDailyCap()) {
                result.put("code", 400);
                result.put("msg", "今日广告奖励总次数已达上限(" + ad.getDailyCap() + "次)");
                return result;
            }
        }
        int balance = pointsService.grantPoints(userId, reward, "AD", adId, ad.getAdName());
        result.put("code", 200);
        result.put("msg", "奖励发放成功");
        result.put("balance", balance);
        result.put("rewardPoints", reward);
        result.put("adName", ad.getAdName());
        return result;
    }
}
