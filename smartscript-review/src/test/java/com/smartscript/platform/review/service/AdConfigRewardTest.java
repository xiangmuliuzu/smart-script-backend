package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.AdConfig;
import com.smartscript.platform.review.mapper.AdConfigMapper;
import com.smartscript.platform.review.mapper.PointsRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 广告观看奖励发放核心逻辑单元测试
 * 覆盖：参数/存在/启用/日期/奖励配置/频次上限/正常发放
 */
@ExtendWith(MockitoExtension.class)
class AdConfigRewardTest {

    @Mock
    private AdConfigMapper adConfigMapper;

    @Mock
    private PointsRecordMapper pointsRecordMapper;

    @Mock
    private PointsService pointsService;

    @InjectMocks
    private AdConfigService adConfigService;

    private AdConfig ad;

    @BeforeEach
    void setUp() {
        ad = new AdConfig();
        ad.setAdId(2L);
        ad.setAdName("测试开屏广告");
        ad.setIsEnabled(1);
        ad.setFrequencyLimit(3);
        ad.setRewardPoints(10);
    }

    @Test
    void reward_缺参数_返回400() {
        assertEquals(400, adConfigService.reward(null, 106L).get("code"));
        assertEquals(400, adConfigService.reward(2L, null).get("code"));
    }

    @Test
    void reward_广告不存在_返回404() {
        when(adConfigMapper.selectAdConfigById(99L)).thenReturn(null);
        assertEquals(404, adConfigService.reward(99L, 106L).get("code"));
    }

    @Test
    void reward_广告未启用_返回400() {
        ad.setIsEnabled(0);
        when(adConfigMapper.selectAdConfigById(2L)).thenReturn(ad);
        Map<String, Object> r = adConfigService.reward(2L, 106L);
        assertEquals(400, r.get("code"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void reward_频次已达上限_返回400() {
        when(adConfigMapper.selectAdConfigById(2L)).thenReturn(ad);
        when(pointsRecordMapper.countAdClaims(eq(106L), eq(2L), anyString())).thenReturn(3);
        Map<String, Object> r = adConfigService.reward(2L, 106L);
        assertEquals(400, r.get("code"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void reward_正常发放_发积分并返回余额() {
        when(adConfigMapper.selectAdConfigById(2L)).thenReturn(ad);
        when(pointsRecordMapper.countAdClaims(eq(106L), eq(2L), anyString())).thenReturn(1);
        when(pointsService.grantPoints(106L, 10, "AD", 2L, "测试开屏广告")).thenReturn(140);

        Map<String, Object> r = adConfigService.reward(2L, 106L);
        assertEquals(200, r.get("code"));
        assertEquals(140, r.get("balance"));
        assertEquals(10, r.get("rewardPoints"));
        verify(pointsService, times(1)).grantPoints(106L, 10, "AD", 2L, "测试开屏广告");
    }

    @Test
    void reward_奖励积分未配置_返回400() {
        ad.setRewardPoints(0);
        when(adConfigMapper.selectAdConfigById(2L)).thenReturn(ad);
        Map<String, Object> r = adConfigService.reward(2L, 106L);
        assertEquals(400, r.get("code"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void reward_广告已过期_返回400() {
        ad.setEndDate(new Date(System.currentTimeMillis() - 86400000L));
        when(adConfigMapper.selectAdConfigById(2L)).thenReturn(ad);
        Map<String, Object> r = adConfigService.reward(2L, 106L);
        assertEquals(400, r.get("code"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }
}
