package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.AdConfig;
import com.smartscript.platform.review.service.AdConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 广告福利任务配置接口测试（WelfareAdController）
 * 验证：
 * 1. list 只查 category=WELFARE 的广告任务
 * 2. 新增/修改强制 category=WELFARE，防被篡改成广告位
 * 3. 删除广告位（PLACEMENT）必须被拒绝，删除 WELFARE 才放行
 */
@ExtendWith(MockitoExtension.class)
class WelfareAdControllerTest {

    @Mock
    private AdConfigService adConfigService;

    @InjectMocks
    private WelfareAdController controller;

    private AdConfig welfareAd;
    private AdConfig placementAd;

    @BeforeEach
    void setUp() {
        welfareAd = new AdConfig();
        welfareAd.setAdId(6L);
        welfareAd.setCategory("WELFARE");
        welfareAd.setAdName("看激励视频得积分");
        welfareAd.setFrequencyLimit(5);
        welfareAd.setRewardPoints(10);
        welfareAd.setIsEnabled(1);

        placementAd = new AdConfig();
        placementAd.setAdId(2L);
        placementAd.setCategory("PLACEMENT");
        placementAd.setAdName("测试开屏广告");
    }

    @Test
    void list_强制WELFARE分类() {
        when(adConfigService.selectAdConfigList(any(AdConfig.class)))
                .thenReturn(Arrays.asList(welfareAd));

        Map<String, Object> r = controller.list(new AdConfig());
        assertEquals(200, r.get("code"));

        ArgumentCaptor<AdConfig> captor = ArgumentCaptor.forClass(AdConfig.class);
        verify(adConfigService).selectAdConfigList(captor.capture());
        assertEquals("WELFARE", captor.getValue().getCategory());
    }

    @Test
    void add_强制WELFARE分类_即使前端传了PLACEMENT() {
        AdConfig req = new AdConfig();
        req.setAdName("新广告任务");
        req.setCategory("PLACEMENT"); // 前端误传，应被覆盖

        Map<String, Object> r = controller.add(req);
        assertEquals(200, r.get("code"));

        ArgumentCaptor<AdConfig> captor = ArgumentCaptor.forClass(AdConfig.class);
        verify(adConfigService).insertAdConfig(captor.capture());
        assertEquals("WELFARE", captor.getValue().getCategory());
        // 默认值补齐
        assertEquals(1, captor.getValue().getIsEnabled());
        assertEquals(5, captor.getValue().getFrequencyLimit());
    }

    @Test
    void edit_强制WELFARE分类() {
        AdConfig req = new AdConfig();
        req.setAdId(6L);
        req.setRewardPoints(15);

        Map<String, Object> r = controller.edit(req);
        assertEquals(200, r.get("code"));

        ArgumentCaptor<AdConfig> captor = ArgumentCaptor.forClass(AdConfig.class);
        verify(adConfigService).updateAdConfig(captor.capture());
        assertEquals("WELFARE", captor.getValue().getCategory());
    }

    @Test
    void remove_福利任务可删() {
        when(adConfigService.selectAdConfigById(6L)).thenReturn(welfareAd);

        Map<String, Object> r = controller.remove(6L);
        assertEquals(200, r.get("code"));
        verify(adConfigService).deleteAdConfigById(6L);
    }

    @Test
    void remove_广告位拒绝删除() {
        when(adConfigService.selectAdConfigById(2L)).thenReturn(placementAd);

        Map<String, Object> r = controller.remove(2L);
        assertEquals(400, r.get("code"));
        verify(adConfigService, never()).deleteAdConfigById(anyLong());
    }

    @Test
    void remove_不存在返回404() {
        when(adConfigService.selectAdConfigById(99L)).thenReturn(null);

        Map<String, Object> r = controller.remove(99L);
        assertEquals(404, r.get("code"));
        verify(adConfigService, never()).deleteAdConfigById(anyLong());
    }

    @Test
    void list_空列表也返回200() {
        when(adConfigService.selectAdConfigList(any(AdConfig.class)))
                .thenReturn(Collections.emptyList());
        Map<String, Object> r = controller.list(new AdConfig());
        assertEquals(200, r.get("code"));
        assertEquals(0, r.get("total"));
    }
}
