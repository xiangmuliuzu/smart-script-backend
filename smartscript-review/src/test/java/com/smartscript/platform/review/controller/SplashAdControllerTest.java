package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.AdConfig;
import com.smartscript.platform.review.mapper.AdConfigMapper;
import com.smartscript.platform.review.service.AdConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SplashAdControllerTest {
    @Mock AdConfigMapper mapper;
    @Mock AdConfigService service;
    @InjectMocks AdConfigController admin;

    @Test void splashReturnsOnlyDisplayFieldsAndBoundsDuration() {
        SplashAdController controller = new SplashAdController(mapper);
        assertEquals(Map.of(), controller.splash().get("data"));
        AdConfig ad = new AdConfig();
        ad.setImageUrl("/profile/upload/ad.png");
        ad.setLinkUrl("https://example.com/");
        ad.setDurationSeconds(30);
        when(mapper.selectActiveSplash()).thenReturn(ad);
        assertEquals(Map.of("imageUrl", "/profile/upload/ad.png", "linkUrl", "https://example.com/", "durationSeconds", 10), controller.splash().get("data"));
    }

    @Test void invalidLinksAndDurationsAreRejectedWithoutWriting() {
        AdConfig ad = new AdConfig();
        ad.setImageUrl("javascript:alert(1)");
        assertEquals(400, admin.add(ad).get("code"));
        ad.setImageUrl("/profile/upload/ad.png");
        ad.setLinkUrl("file:///secret");
        assertEquals(400, admin.edit(ad).get("code"));
        ad.setLinkUrl("");
        ad.setDurationSeconds(11);
        assertEquals(400, admin.edit(ad).get("code"));
        verifyNoInteractions(service);
    }
}
