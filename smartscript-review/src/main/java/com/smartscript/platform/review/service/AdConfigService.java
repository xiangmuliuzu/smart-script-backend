package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.AdConfig;
import com.smartscript.platform.review.mapper.AdConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 广告配置Service业务层处理
 *
 * @author smartscript
 */
@Service
public class AdConfigService {

    @Autowired
    private AdConfigMapper adConfigMapper;

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
}
