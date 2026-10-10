package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.AdConfig;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 广告配置Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface AdConfigMapper {

    List<AdConfig> selectAdConfigList(AdConfig adConfig);

    AdConfig selectAdConfigById(Long adId);

    int insertAdConfig(AdConfig adConfig);

    int updateAdConfig(AdConfig adConfig);

    int deleteAdConfigById(Long adId);
}
